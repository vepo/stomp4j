package dev.vepo.stomp4j.server.channels;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.AcknowledgedOutboundChannel;
import dev.vepo.stomp4j.server.OutboundChannel;
import dev.vepo.stomp4j.server.SubscriberAckListener;
import dev.vepo.stomp4j.server.session.Session;
import dev.vepo.stomp4j.server.session.SessionCloser;
import dev.vepo.stomp4j.server.session.Status;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.PfxOptions;

public class WebSocketChannel implements Channel {
    private class WebSocketExternalOutboundChannel implements AcknowledgedOutboundChannel {

        @Override
        public void send(Message message) {
            send(message, null);
        }

        @Override
        public void send(Message message, SubscriberAckListener listener) {
            logger.debug("Sending message to all active sessions: count={}", activeSessions.size());
            var ackListener = java.util.Optional.ofNullable(listener);
            activeSessions.keySet().forEach(session -> session.handle(message, ackListener));
        }
    }

    private class WebSocketSessionOutboundChannel implements OutboundChannel {
        private final ServerWebSocket webSocket;

        private WebSocketSessionOutboundChannel(ServerWebSocket webSocket) {
            this.webSocket = webSocket;
        }

        @Override
        public void send(Message message) {
            try {
                webSocket.writeFinalTextFrame(message.encode());
            } catch (Exception ex) {
                logger.error("Error sending message to WebSocket", ex);
            }
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(WebSocketChannel.class);

    private final int port;
    private final ChannelListener listener;
    private final ChannelRuntime runtime;
    private final AtomicBoolean running;
    private final Map<Session, ServerWebSocket> activeSessions;
    private final WebSocketExternalOutboundChannel outboundChannel;
    private final SessionCloser sessionCloser;

    private Vertx vertx;
    private HttpServer server;
    private CountDownLatch listenLatch;

    public WebSocketChannel(int port, ChannelListener listener, ChannelRuntime runtime) {
        this.port = port;
        this.listener = listener;
        this.runtime = runtime;
        this.running = new AtomicBoolean(false);
        this.activeSessions = new ConcurrentHashMap<>();
        this.outboundChannel = new WebSocketExternalOutboundChannel();
        this.sessionCloser = this::closeSession;
    }

    @Override
    public void close() {
        logger.info("Closing WebSocket channel... port={}", port);
        this.running.set(false);

        activeSessions.forEach((session, webSocket) -> {
            if (!webSocket.isClosed()) {
                webSocket.close();
            }
        });
        activeSessions.clear();

        if (Objects.nonNull(server)) {
            server.close()
                  .onSuccess(v -> logger.info("WebSocket server closed"))
                  .onFailure(error -> logger.error("Error closing WebSocket server", error));
        }

        if (Objects.nonNull(vertx)) {
            vertx.close()
                 .onSuccess(v -> logger.info("Vertx closed"))
                 .onFailure(error -> logger.error("Error closing Vertx", error));
        }

        logger.info("WebSocket channel closed port={}", port);
    }

    private void closeSession(Session session) {
        var webSocket = activeSessions.remove(session);
        if (Objects.nonNull(webSocket) && !webSocket.isClosed()) {
            webSocket.close();
        }
        if (session.status() != Status.END) {
            listener.sessionDisconnected(session);
        }
    }

    @Override
    public OutboundChannel outboundChannel() {
        return outboundChannel;
    }

    @Override
    public void start() {
        logger.info("Starting WebSocket Channel at port {}", port);

        vertx = Vertx.vertx();
        var options = new HttpServerOptions().setPort(port);
        runtime.sslSettings().ifPresent(sslSettings -> {
            options.setSsl(true);
            sslSettings.keyStoreLocation().ifPresent(keyStore -> options.setKeyCertOptions(
                                                                                           new PfxOptions().setPath(keyStore.path())
                                                                                                           .setPassword(keyStore.password())));
        });

        server = vertx.createHttpServer(options);

        server.webSocketHandler(webSocket -> {
            if (!running.get()) {
                webSocket.close((short) 1001, "Server stopped");
                return;
            }

            logger.info("New WebSocket connection: {}", webSocket.remoteAddress());

            var sessionOutboundChannel = new WebSocketSessionOutboundChannel(webSocket);
            var session = new Session(sessionOutboundChannel,
                                      listener,
                                      runtime.sessionConfig(),
                                      sessionCloser,
                                      runtime.heartbeatExecutor());
            activeSessions.put(session, webSocket);

            webSocket.textMessageHandler(text -> {
                var bytes = text.getBytes();
                session.offer(bytes, bytes.length);
            });

            webSocket.binaryMessageHandler(buffer -> session.offer(buffer.getBytes(), buffer.length()));

            webSocket.closeHandler(v -> closeSession(session));
            webSocket.exceptionHandler(ex -> {
                logger.error("WebSocket error", ex);
                closeSession(session);
            });

            logger.info("WebSocket session started, waiting for CONNECT: {}", session);
        });

        listenLatch = new CountDownLatch(1);
        var listenFailure = new AtomicReference<Throwable>();
        server.listen()
              .onSuccess(httpServer -> {
                  running.set(true);
                  logger.info("WebSocket server started on port {}", httpServer.actualPort());
                  listenLatch.countDown();
              })
              .onFailure(error -> {
                  logger.error("Failed to start WebSocket server on port {}", port, error);
                  listenFailure.set(error);
                  listenLatch.countDown();
              });
        try {
            if (!listenLatch.await(10, TimeUnit.SECONDS)) {
                failStart(new IllegalStateException("WebSocket server did not start in time on port %d".formatted(port)));
            }
            if (!running.get()) {
                failStart(new IllegalStateException("Failed to start WebSocket server on port %d".formatted(port),
                                                    listenFailure.get()));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failStart(new IllegalStateException("Interrupted while starting WebSocket server", ex));
        }
    }

    private void failStart(IllegalStateException failure) {
        close();
        throw failure;
    }

    @Override
    public String toString() {
        return "WebSocketChannel[port=%d]".formatted(port);
    }
}
