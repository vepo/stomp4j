package io.vepo.stomp4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vepo.stomp4j.exceptions.StompException;
import io.vepo.stomp4j.protocol.Header;
import io.vepo.stomp4j.protocol.Message;
import io.vepo.stomp4j.protocol.Stomp;
import io.vepo.stomp4j.protocol.StompListener;
import io.vepo.stomp4j.protocol.Transport;
import io.vepo.stomp4j.protocol.transport.TcpTransport;
import io.vepo.stomp4j.protocol.transport.WebSocketTransport;

public final class StompClient implements AutoCloseable, StompListener {

    public enum TransportType {
        WEB_SOCKET, TCP
    }

    private final static Logger logger = LoggerFactory.getLogger(StompClient.class);

    private final CountDownLatch connectedLatch;
    private final Object lock = new Object();
    private final AtomicReference<Stomp> selectedProtocol = new AtomicReference<>();
    private UserCredential credentials;
    private Transport transport;
    private final Map<String, Consumer<String>> consumers = new HashMap<>();
    private final Map<String, Queue<Message>> pulledMessages = Collections.synchronizedMap(new HashMap<>());
    private final ScheduledExecutorService heartBeatService;
    private ScheduledFuture<?> heartBeatTask;

    private Set<Stomp> protocols;
    private Optional<String> session;

    public StompClient(String url) {
        this(url, null, Stomp.ALL_VERSIONS);
    }

    public StompClient(String url, TransportType transportType) {
        this(url, null, transportType, Stomp.ALL_VERSIONS);
    }

    public StompClient(String url, UserCredential credentials) {
        this(url, credentials, Stomp.ALL_VERSIONS);
    }

    public StompClient(String url, TransportType transportType, UserCredential credentials) {
        this(url, credentials, transportType, Stomp.ALL_VERSIONS);
    }

    public StompClient(String url, UserCredential credentials, Set<Stomp> protocols) {
        this(url, credentials, null, protocols);
    }

    public StompClient(String url, UserCredential credentials, TransportType transportType, Set<Stomp> protocols) {
        try {
            this.protocols = protocols;
            if (Objects.isNull(transportType)) {
                this.transport = Transport.create(new URI(url), this);
            } else {
                this.transport = switch (transportType) {
                    case WEB_SOCKET -> new WebSocketTransport(new URI(url), this);
                    case TCP -> new TcpTransport(new URI(url), this);
                };
            }
            ;
            this.session = Optional.empty();
            this.credentials = credentials;
            this.heartBeatService = Executors.newSingleThreadScheduledExecutor();
            this.heartBeatTask = null;
            this.connectedLatch = new CountDownLatch(1);
        } catch (URISyntaxException e) {
            logger.error("Invalid URL!", e);
            throw new StompException("Error creating StompClient", e);
        }
    }

    @Override
    public void connected(Transport transport) {
        logger.info("Connected with server {}", transport);
        transport.send(Stomp.connect(transport.host(), credentials, protocols));
    }

    @Override
    public void message(Message message) {
        logger.info("Received message: {}", message);
        switch (message.command()) {
            case CONNECTED:
                connected(message);
                break;
            case MESSAGE:
                message.headers()
                       .get(Header.DESTINATION)
                       .ifPresentOrElse(topic -> {
                           if (consumers.containsKey(topic)) {
                               consumers.get(topic).accept(message.payload());
                           } else {
                               pulledMessages.computeIfAbsent(topic, k -> new LinkedList<>())
                                             .add(message);
                           }
                       }, () -> {
                           logger.warn("No topic found in message: {}", message);
                       });
                selectedProtocol.get().onMessage(message, session, transport);
                break;
            default:
                break;
        }
    }

    @Override
    public void error(Message message) {
        logger.error("Error message: {}", message);
    }

    public void connect() {
        logger.info("Connecting with server {}", transport);
        transport.connect();
        logger.info("Waiting for client connection");
        try {
            connectedLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("Client connected");
    }

    public StompClient subscribe(String topic) {
        this.selectedProtocol.get().subscribe(topic, session, this.transport);
        return this;
    }

    public void join() {
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public StompClient subscribe(String topic, Consumer<String> consumer) {
        this.consumers.put(topic, consumer);
        this.selectedProtocol.get().subscribe(topic, this.session, this.transport);
        return this;
    }

    @Override
    public void close() {
        logger.info("Stoping Stomp client");
        transport.close();
        logger.info("Stoping heart beat service");
        if (Objects.nonNull(heartBeatTask)) {
            heartBeatTask.cancel(false);
        }
        logger.info("Shutting down heart beat service");
        heartBeatService.shutdown();
        logger.info("Stomp client stopped");
        synchronized (lock) {
            lock.notify();
        }
    }

    public StompClient unsubscribe(String topic) {
        // this.selectedProtocol.get().unsubscribe(topic, webSocket);
        return this;
    }

    private void connected(Message message) {
        logger.info("Connected with server {}", message);
        session = message.headers().get(Header.SESSION);
        selectedProtocol.set(Stomp.getProtocol(message.headers().version(),
                                               protocols));
        if (selectedProtocol.get().hasHeartBeat()) {
            logger.info("Heart beat is enabled");
            logger.info("Heart beat interval: {}", message.headers().get("heart-beat"));
            message.headers()
                   .get("heart-beat")
                   .map(heartBeatInterval -> {
                       String[] heartBeats = heartBeatInterval.split(",");
                       return Math.round(0.7 * Integer.parseInt(heartBeats[1]));
                   }).ifPresent(interval -> {
                       if (interval > 0) {
                           logger.info("Setting up heart beat with interval: {}", interval);
                           heartBeatTask = heartBeatService.scheduleAtFixedRate(() -> {
                               logger.info("Sending heart beat message");
                               if (transport.silentTime() > interval) {
                                   transport.send(selectedProtocol.get().heartBeatMessage());
                               }
                           }, 0, interval, TimeUnit.MILLISECONDS);
                       } else {
                           logger.info("Heart beat interval is zero. Disabling heart beat");
                       }
                   });
        }
        connectedLatch.countDown();
        logger.info("Client connected");
    }
}
