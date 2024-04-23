package io.vepo.stomp4j.protocol.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vepo.stomp4j.ClientKey;
import io.vepo.stomp4j.protocol.Message;
import io.vepo.stomp4j.protocol.StompListener;
import io.vepo.stomp4j.protocol.Transport;

public class WebSocketTransport implements Transport {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketTransport.class);
    private final HttpClient httpClient;
    private final ClientKey key;
    private final URI uri;
    private final StompListener listener;
    private WebSocket webSocketClient;

    public WebSocketTransport(URI uri, StompListener listener) {
        this.uri = uri;
        this.listener = listener;
        this.httpClient = HttpClient.newHttpClient();
        this.key = new ClientKey();
    }

    @Override
    public void send(String message) {
        webSocketClient.sendText(message, true);
        webSocketClient.request(1);
    }

    @Override
    public void connect() {
        var byteArrayOutputStream = new ByteArrayOutputStream();
        var byteChannel = Channels.newChannel(byteArrayOutputStream);
        var buffer = new StringBuffer();
        logger.info("Open WebSocket connection with: {}", uri);
        httpClient.newWebSocketBuilder()
                  .header("uuid", key.toHex())
                  .buildAsync(uri, new WebSocket.Listener() {
                      @Override
                      public void onOpen(WebSocket webSocket) {
                          logger.info("Connection open!");
                          webSocketClient = webSocket;
                          listener.connected(WebSocketTransport.this);
                      }

                      @Override
                      public void onError(WebSocket webSocket, Throwable error) {
                          logger.error("Error on WebSocket connection!", error);
                          // listener.error(error);
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
                              listener.message(Message.readMessage(buffer.toString()));
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

    @Override
    public String host() {
        return uri.getHost();
    }

    @Override
    public void close() {
        if (Objects.nonNull(webSocketClient)) {
            webSocketClient.sendClose(WebSocket.NORMAL_CLOSURE, "Client closed connection");
        }
        httpClient.close();
    }

    @Override
    public long silentTime() {
        return 0;
    }

    @Override
    public long nextId() {
        return 0;
    }

}
