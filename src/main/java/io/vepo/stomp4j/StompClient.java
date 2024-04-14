package io.vepo.stomp4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vepo.stomp4j.exceptions.StompException;
import io.vepo.stomp4j.port.WebSocketListener;
import io.vepo.stomp4j.port.WebSocketPort;
import io.vepo.stomp4j.protocol.Command;
import io.vepo.stomp4j.protocol.Message;
import io.vepo.stomp4j.protocol.Stomp;
import io.vepo.stomp4j.protocol.StompEventListener;

public final class StompClient implements AutoCloseable, StompEventListener {

    private final static Logger logger = LoggerFactory.getLogger(StompClient.class);

    private final Object connectedLock = new Object();
    private final Object closeLock = new Object();
    private final AtomicReference<Stomp> selectedProtocol = new AtomicReference<>();
    private boolean isClientConnected = false;
    private UserCredential credentials;
    private WebSocketPort webSocket;
    private final Map<String, Consumer<String>> consumers = new HashMap<>();
    private final ScheduledExecutorService heartBeatService;
    private ScheduledFuture<?> heartBeatTask;

    public StompClient(String url) {
        this(url, null);
    }

    public StompClient(String url, UserCredential credentials) {
        this.webSocket = new WebSocketPort(url, new ClientKey());
        this.credentials = credentials;
        this.heartBeatService = Executors.newSingleThreadScheduledExecutor();
        this.heartBeatTask = null;
    }

    @Override
    public void message(Message message) {
        logger.info("Received message: {}", message);
        var consumer = consumers.get(message.headers().destination().orElseThrow(() -> new StompException("Destination not found.")));
        if (consumer != null) {
            consumer.accept(message.payload());
        }
        if (selectedProtocol.get().shouldAcknowledge()) {
            webSocket.send(selectedProtocol.get().acknowledge(message));
        }
    }

    @Override
    public void connected() {
        emitClientConnected();
    }

    @Override
    public void error(Message message) {
        logger.error("Error message: {}", message);
    }

    public void connect() {
        logger.info("Connecting with server {}", webSocket);
        try {
            webSocket.connect(new WebSocketListener() {

                @Override
                public void connectionOpened() {
                    webSocket.send(Stomp.connect(webSocket.url(), credentials));
                }

                @Override
                public void messageReceived(String content) {
                    logger.info("Received message: {}", content);
                    var message = Stomp.readMessage(content);
                    logger.info("Received message: {}", message);
                    if (message.command() == Command.CONNECTED) {
                        selectedProtocol.set(Stomp.getProtocol(message.headers().version()));
                        if (selectedProtocol.get().hasHeartBeat()) {
                            logger.info("Heart beat is enabled");
                            logger.info("Heart beat interval: {}", message.headers().get("heart-beat"));
                            message.headers()
                                   .get("heart-beat")
                                   .map(heartBeatInterval -> {
                                       String[] heartBeats = heartBeatInterval.split(",");
                                       return Math.round(0.7 * Integer.parseInt(heartBeats[1]));
                                   }).ifPresent(interval -> {
                                       logger.info("Setting up heart beat with interval: {}", interval);
                                       heartBeatTask = heartBeatService.scheduleAtFixedRate(() -> {
                                           logger.info("Sending heart beat message");
                                           if (webSocket.silentTime() > interval) {
                                               webSocket.send(selectedProtocol.get().heartBeatMessage());
                                           }
                                       }, interval, interval, TimeUnit.MILLISECONDS);
                                   });
                        }
                    }
                    selectedProtocol.get().handleMessage(message, StompClient.this);

                }

                @Override
                public void error(Throwable error) {
                    logger.error("Error on WebSocket connection!", error);
                }

            });

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.error("Error communicating with server", e);
        }

        awaitClientConnection();

    }

    public StompClient subscribe(String topic) {
        this.selectedProtocol.get().subscribe(topic, webSocket);
        return this;
    }

    public void join() {
        synchronized (closeLock) {
            try {
                closeLock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public StompClient subscribe(String topic, Consumer<String> consumer) {
        this.consumers.put(topic, consumer);
        this.selectedProtocol.get().subscribe(topic, webSocket);
        return this;
    }

    @Override
    public void close() {
        logger.info("Stoping Stomp client");
        webSocket.close();
        heartBeatTask.cancel(false);
        heartBeatService.shutdown();
        synchronized (closeLock) {
            closeLock.notify();
        }
    }

    /**
     * Emits that the client is connected.
     */
    private void emitClientConnected() {
        synchronized (connectedLock) {
            connectedLock.notify();
        }
    }

    /**
     * Awaits if necessary until the websocket client is connected.
     */
    private void awaitClientConnection() {
        synchronized (connectedLock) {
            if (!isClientConnected)
                try {
                    connectedLock.wait();
                    isClientConnected = true;
                } catch (InterruptedException e) {
                    logger.error("[Stomp client] got an unexpected exception", e);
                    throw new StompException("unexpected exception " + e.getMessage());
                }
        }
    }
}
