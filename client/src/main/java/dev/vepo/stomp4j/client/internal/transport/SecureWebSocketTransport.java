package dev.vepo.stomp4j.client.internal.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Message;

public class SecureWebSocketTransport implements Transport {
    private static final Logger logger = LoggerFactory.getLogger(SecureWebSocketTransport.class);

    private static SSLContext defaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load default SSL context", ex);
        }
    }

    private final HttpClient httpClient;
    private final ClientKey key;
    private final URI uri;
    private final TransportListener listener;
    private WebSocket webSocketClient;

    private volatile long lastReceivedMessage;

    public SecureWebSocketTransport(URI uri, TransportListener listener) {
        this(uri, listener, defaultSslContext());
    }

    public SecureWebSocketTransport(URI uri, TransportListener listener, SSLContext sslContext) {
        this.uri = uri;
        this.listener = listener;
        this.httpClient = HttpClient.newBuilder().sslContext(sslContext).build();
        this.key = new ClientKey();
        this.lastReceivedMessage = System.nanoTime();
    }

    @Override
    public void close() {
        if (Objects.nonNull(webSocketClient)) {
            webSocketClient.sendClose(WebSocket.NORMAL_CLOSURE, "Client closed connection");
        }
        httpClient.close();
    }

    @Override
    public void connect() {
        var byteArrayOutputStream = new ByteArrayOutputStream();
        var byteChannel = Channels.newChannel(byteArrayOutputStream);
        var buffer = new StringBuffer();
        logger.info("Opening secure WebSocket connection with: {}", uri);
        httpClient.newWebSocketBuilder()
                  .header("uuid", key.toHex())
                  .buildAsync(uri, new WebSocket.Listener() {
                      @Override
                      public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                          return handleData(webSocket, data, last, byteArrayOutputStream, byteChannel, buffer);
                      }

                      @Override
                      public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                          logger.warn("Secure connection closed! statusCode={}, reason={}", statusCode, reason);
                          return null;
                      }

                      @Override
                      public void onError(WebSocket webSocket, Throwable error) {
                          logger.error("Error on secure WebSocket connection!", error);
                          listener.onError(new Message(Command.ERROR,
                                                       dev.vepo.stomp4j.commons.protocol.Headers.builder()
                                                                                                .with("message", error.getMessage())
                                                                                                .build(),
                                                       error.getMessage()));
                      }

                      @Override
                      public void onOpen(WebSocket webSocket) {
                          logger.info("Secure connection open!");
                          webSocketClient = webSocket;
                          lastReceivedMessage = System.nanoTime();
                          listener.onConnected(SecureWebSocketTransport.this);
                      }

                      @Override
                      public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                          try {
                              byteChannel.write(StandardCharsets.UTF_8.encode(CharBuffer.wrap(data)));
                          } catch (IOException ex) {
                              throw new RuntimeException(ex);
                          }
                          return handleBuffered(webSocket, last, byteArrayOutputStream, buffer);
                      }
                  });
    }

    private CompletionStage<?> handleBuffered(WebSocket webSocket,
                                              boolean last,
                                              ByteArrayOutputStream byteArrayOutputStream,
                                              StringBuffer buffer) {
        var value = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
        byteArrayOutputStream.reset();
        buffer.append(StandardCharsets.UTF_8.decode(value));
        if (last) {
            lastReceivedMessage = System.nanoTime();
            listener.onMessage(Message.readMessage(buffer.toString()));
            buffer.setLength(0);
        }
        webSocket.request(1);
        return null;
    }

    private CompletionStage<?> handleData(WebSocket webSocket,
                                          ByteBuffer data,
                                          boolean last,
                                          ByteArrayOutputStream byteArrayOutputStream,
                                          java.nio.channels.WritableByteChannel byteChannel,
                                          StringBuffer buffer) {
        try {
            byteChannel.write(data);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return handleBuffered(webSocket, last, byteArrayOutputStream, buffer);
    }

    @Override
    public String host() {
        return uri.getHost();
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
            webSocketClient.sendText(message.encode(), true);
            webSocketClient.request(1);
        } catch (RuntimeException ex) {
            throw TransportFailures.sendFailed(ex);
        }
    }

    @Override
    public long silentTime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastReceivedMessage);
    }
}
