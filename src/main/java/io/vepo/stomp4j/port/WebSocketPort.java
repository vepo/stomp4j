package io.vepo.stomp4j.port;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vepo.stomp4j.ClientKey;

public class WebSocketPort implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketPort.class);
    private final String url;
    private final ClientKey key;
    private final HttpClient httpClient;

    private WebSocket webSocket;
    private AtomicLong id;
    private long lastSendMessage;

    public WebSocketPort(String url, ClientKey key) {
        this.url = url;
        this.key = key;
        this.httpClient = HttpClient.newHttpClient();
        this.id = new AtomicLong(0);
        this.lastSendMessage = System.nanoTime();
    }

    public String url() {
        return url;
    }

    public long currentId() {
        return id.get();
    }

    public long nextId() {
        return id.incrementAndGet();
    }

    public void connect(WebSocketListener listener) throws InterruptedException, ExecutionException {
        var byteArrayOutputStream = new ByteArrayOutputStream();
        var byteChannel = Channels.newChannel(byteArrayOutputStream);
        var buffer = new StringBuffer();
        httpClient.newWebSocketBuilder()
                  .header("uuid", key.toHex())
                  .buildAsync(URI.create(url), new WebSocket.Listener() {
                      @Override
                      public void onOpen(WebSocket webSocket) {
                          logger.info("Connection open!");
                          WebSocketPort.this.webSocket = webSocket;
                          listener.connectionOpened();
                      }

                      @Override
                      public void onError(WebSocket webSocket, Throwable error) {
                          logger.error("Error on WebSocket connection!", error);
                          listener.error(error);
                      };

                      @Override
                      public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                          logger.info("Binary data received! last={}", last);
                          try {
                              byteChannel.write(data);
                          } catch (IOException e) {
                              throw new RuntimeException(e);
                          }
                          var value = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
                          byteArrayOutputStream.reset();
                          buffer.append(new String(value.array()));

                          if (last) {
                              listener.messageReceived(buffer.toString());
                              buffer.setLength(0);
                          } else {
                              logger.info("Data until now: {}", buffer.toString());
                          }
                          webSocket.request(1);
                          return null;
                      }

                      @Override
                      public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
                          logger.info("Ping received!");
                          webSocket.request(1);
                          return null;
                      }

                      @Override
                      public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
                          logger.info("Pong received!");
                          webSocket.request(1);
                          return null;
                      }

                      @Override
                      public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                          logger.info("Text data received! last={}", last);
                          // listener.messageReceived(data.toString());
                          webSocket.request(1);
                          return null;
                      }

                      @Override
                      public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                          logger.warn("Connection closed! statusCode={}, reason={}", statusCode, reason);
                          return null;
                      }
                  });

        logger.info("WebSocket connection established!");
    }

    public long silentTime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastSendMessage);
    }

    public void send(String message) {
        logger.info("Sending message: {}", message);
        webSocket.sendText(message, true);
        webSocket.request(1);
        this.lastSendMessage = System.nanoTime();
    }

    public void close() {
        logger.info("Closing WebSocket connection!");
        if (Objects.nonNull(webSocket)) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Closing connection").join();
        }
        this.httpClient.close();
    }

    @Override
    public String toString() {
        return String.format("WebSocketPort [url=%s, key=%s]", url, key);
    }

}
