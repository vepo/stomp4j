package dev.vepo.stomp4j.client.internal.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Message;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Open a STOMP-over-WebSocket session with
 * {@link HttpClient}, deliver inbound frames via
 * {@link WebSocketInboundFramer}, and send outbound frames from caller
 * threads.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> {@link WebSocketInboundFramer},
 * {@link TransportListener}, {@link ClientKey}
 * </p>
 * <p>
 * <b>Not responsible for:</b> STOMP protocol negotiation, heart-beat
 * scheduling, TLS context provisioning beyond optional {@link SSLContext}.
 * </p>
 */
public class WebSocketTransport implements Transport {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketTransport.class);

    private static int connectTimeoutSeconds() {
        return Integer.getInteger("stomp4j.websocket.connectTimeoutSeconds", 30);
    }

    private final HttpClient httpClient;
    private final ClientKey key;
    private final URI uri;
    private final TransportListener listener;
    private final boolean secure;
    private final WebSocketInboundFramer framer = new WebSocketInboundFramer();
    private volatile long lastSentMessage = System.nanoTime();
    private final Object sendLock = new Object();
    private WebSocket webSocketClient;
    private CompletableFuture<WebSocket> connectFuture;
    private final AtomicBoolean connectFailed = new AtomicBoolean();
    private final CountDownLatch openLatch = new CountDownLatch(1);
    private final CountDownLatch closeLatch = new CountDownLatch(1);

    public WebSocketTransport(URI uri, TransportListener listener) {
        this(uri, listener, null);
    }

    WebSocketTransport(URI uri, TransportListener listener, SSLContext sslContext) {
        this.uri = uri;
        this.listener = listener;
        this.secure = Objects.nonNull(sslContext);
        this.httpClient = Objects.isNull(sslContext)
                                                     ? HttpClient.newHttpClient()
                                                     : HttpClient.newBuilder().sslContext(sslContext).build();
        this.key = new ClientKey();
    }

    private void abortConnectAttempt() {
        if (Objects.nonNull(connectFuture)) {
            connectFuture.whenComplete((webSocket, error) -> {
                if (Objects.nonNull(webSocket)) {
                    webSocket.abort();
                }
            });
        }
        if (Objects.nonNull(webSocketClient)) {
            webSocketClient.abort();
            webSocketClient = null;
        }
    }

    private void awaitWebSocketOpen() {
        try {
            if (!openLatch.await(connectTimeoutSeconds(), TimeUnit.SECONDS)) {
                throw TransportFailures.connectFailed(uri.toString(),
                                                      new IllegalStateException("WebSocket open timed out"));
            }
        } catch (InterruptedException ex) {
            throw TransportFailures.connectFailed(uri.toString(), ex);
        }
        if (connectFailed.get() || Objects.isNull(webSocketClient)) {
            throw TransportFailures.connectFailed(uri.toString(),
                                                  new IllegalStateException("WebSocket connect failed"));
        }
    }

    @Override
    public void close() {
        if (Objects.nonNull(webSocketClient)) {
            try {
                webSocketClient.sendClose(WebSocket.NORMAL_CLOSURE, "Client closed connection");
                if (!closeLatch.await(2, TimeUnit.SECONDS)) {
                    logger.debug("WebSocket close frame not acknowledged; aborting connection");
                    webSocketClient.abort();
                }
            } catch (InterruptedException ex) {
                logger.debug("Interrupted while waiting for WebSocket close; aborting connection", ex);
                webSocketClient.abort();
            } finally {
                webSocketClient = null;
            }
        }
        httpClient.close();
    }

    @Override
    public void connect() {
        logger.info("Opening{} WebSocket connection with: {}", secure ? " secure" : "", uri);
        connectFailed.set(false);
        try {
            connectFuture = httpClient.newWebSocketBuilder()
                                      .header("uuid", key.toHex())
                                      .subprotocols("v12.stomp", "v11.stomp", "v10.stomp", "stomp")
                                      .buildAsync(uri, newListener());
            awaitWebSocketOpen();
        } catch (StompException ex) {
            logger.error("WebSocket connect failed for {}", uri, ex);
            abortConnectAttempt();
            close();
            throw ex;
        }
    }

    @Override
    public String host() {
        return uri.getHost();
    }

    private WebSocket.Listener newListener() {
        return new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                var bytes = new byte[data.remaining()];
                data.get(bytes);
                framer.offer(bytes, listener);
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                logger.warn("WebSocket connection closed statusCode={}, reason={}", statusCode, reason);
                closeLatch.countDown();
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                logger.error("WebSocket connection error", error);
                connectFailed.set(true);
                openLatch.countDown();
                closeLatch.countDown();
                if (Objects.isNull(webSocketClient)) {
                    webSocket.abort();
                }
                listener.onError(new Message(Command.ERROR,
                                             dev.vepo.stomp4j.commons.protocol.Headers.builder()
                                                                                      .with("message", error.getMessage())
                                                                                      .build(),
                                             error.getMessage()));
            }

            @Override
            public void onOpen(WebSocket webSocket) {
                logger.info("WebSocket connection open");
                webSocketClient = webSocket;
                webSocket.request(1);
                openLatch.countDown();
                listener.onConnected(WebSocketTransport.this);
            }

            @Override
            public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                framer.offer(data, listener);
                webSocket.request(1);
                return null;
            }
        };
    }

    @Override
    public long outboundSilentTime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastSentMessage);
    }

    @Override
    public void send(Message message) {
        if (Objects.isNull(webSocketClient)) {
            throw TransportFailures.notConnected();
        }
        logger.atDebug()
              .addArgument(() -> Message.formatted(message.encode()))
              .log("Sending message: {}");
        try {
            synchronized (sendLock) {
                webSocketClient.sendText(message.encode(), true);
                webSocketClient.request(1);
                lastSentMessage = System.nanoTime();
            }
        } catch (RuntimeException ex) {
            throw TransportFailures.sendFailed(ex);
        }
    }

    @Override
    public long silentTime() {
        return framer.silentTime();
    }
}
