package dev.vepo.stomp4j.server.channels;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.OutboundChannel;
import dev.vepo.stomp4j.server.session.Session;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.Router;

public class WebSocketChannel implements Channel {
    private class WebSocketExternalOutboundChannel implements OutboundChannel {
        @Override
        public void send(Message message) {
            logger.info("Message received! sending to all active sessions: activeSessions={} message={}",
                        activeSessions.size(), message);
            activeSessions.forEach(session -> session.handle(message));
        }
    }

    private class WebSocketSessionOutboundChannel implements OutboundChannel {
        private final ServerWebSocket webSocket;

        private WebSocketSessionOutboundChannel(ServerWebSocket webSocket) {
            this.webSocket = webSocket;
        }

        @Override
        public void send(Message message) {
            logger.info("Sending message to client! message={}", message);
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
    private final ExecutorService threadPool;
    private final AtomicBoolean running;
    private final Set<dev.vepo.stomp4j.server.session.Session> activeSessions;
    private final WebSocketExternalOutboundChannel outboundChannel;

    private Vertx vertx;
    private HttpServer server;

    public WebSocketChannel(int port, ChannelListener listener) {
        this.port = port;
        this.listener = listener;
        this.threadPool = Executors.newFixedThreadPool(10);
        this.running = new AtomicBoolean(false);
        this.outboundChannel = new WebSocketExternalOutboundChannel();
        this.activeSessions = Collections.newSetFromMap(new WeakHashMap<>());
    }

    @Override
    public void start() {
        logger.info("Starting WebSocket Channel at port {}", port);
        this.running.set(true);

        vertx = Vertx.vertx();
        Router router = Router.router(vertx);

        server = vertx.createHttpServer(new HttpServerOptions().setPort(port));

        server.webSocketHandler(webSocket -> {
            if (!running.get()) {
                return;
            }

            logger.info("New WebSocket connection: {}", webSocket.remoteAddress());

            // Create session and outbound channel
            var sessionOutboundChannel = new WebSocketSessionOutboundChannel(webSocket);
            var session = new Session(sessionOutboundChannel, this.listener);
            this.activeSessions.add(session);

            // Set up message handler
            webSocket.textMessageHandler(text -> {
                logger.info("Received WebSocket message: {}", text);
                process(text.getBytes(), text.length(), session);
            });

            webSocket.binaryMessageHandler(buffer -> {
                logger.info("Received WebSocket binary message, length: {}", buffer.length());
                session.offer(buffer.getBytes(), buffer.length());
                process(buffer.getBytes(), buffer.length(), session);
            });

            // Handle connection close
            webSocket.closeHandler(v -> {
                logger.info("WebSocket closed: {}", webSocket.remoteAddress());
                this.activeSessions.remove(session);
            });

            webSocket.exceptionHandler(ex -> {
                logger.error("WebSocket error", ex);
                this.activeSessions.remove(session);
            });

            logger.info("WebSocket session started! Waiting for STOMP CONNECT message... {}", session);
        });

        // Start the server
        server.listen()
              .onSuccess(httpServer -> {
                  logger.info("WebSocket server started on port {}", httpServer.actualPort());
              })
              .onFailure(error -> {
                  logger.error("Failed to start WebSocket server on port {}", port, error);
                  running.set(false);
              });
    }

    private void process(byte[] data, int length, Session session) {
        session.offer(data, length);
    }

    @Override
    public void close() {
        logger.info("Closing WebSocket channel... port={}", port);
        this.running.set(false);

        // Close all active WebSocket connections
        activeSessions.clear();

        // Close the HTTP server
        if (server != null) {
            server.close()
                  .onSuccess(v -> logger.info("WebSocket server closed"))
                  .onFailure(error -> logger.error("Error closing WebSocket server", error));
        }

        // Close Vertx instance
        if (vertx != null) {
            vertx.close()
                 .onSuccess(v -> logger.info("Vertx closed"))
                 .onFailure(error -> logger.error("Error closing Vertx", error));
        }

        // Shutdown thread pool
        try {
            threadPool.shutdown();
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException ex) {
            logger.error("Thread pool shutdown interrupted", ex);
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("WebSocket channel closed!!! port={}", port);
    }

    @Override
    public OutboundChannel outboundChannel() {
        return this.outboundChannel;
    }

    @Override
    public String toString() {
        return "WebSocketChannel[port=%d]".formatted(port);
    }
}