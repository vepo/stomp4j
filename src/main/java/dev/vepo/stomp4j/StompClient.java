package dev.vepo.stomp4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.exceptions.StompException;
import dev.vepo.stomp4j.protocol.Header;
import dev.vepo.stomp4j.protocol.Message;
import dev.vepo.stomp4j.protocol.Stomp;
import dev.vepo.stomp4j.protocol.Transport;
import dev.vepo.stomp4j.protocol.TransportListener;
import dev.vepo.stomp4j.protocol.transport.TcpTransport;
import dev.vepo.stomp4j.protocol.transport.WebSocketTransport;

public final class StompClient implements AutoCloseable, TransportListener {

    private final static Logger logger = LoggerFactory.getLogger(StompClient.class);

    private final CountDownLatch connectedLatch;
    private final Object lock = new Object();
    private final AtomicReference<Stomp> selectedProtocol = new AtomicReference<>();
    private UserCredential credentials;
    private Transport transport;
    private final Map<Subscription, Consumer<String>> consumers = new HashMap<>();
    private final Map<Subscription, Queue<Message>> receivedMessages = Collections.synchronizedMap(new HashMap<>());
    private final Set<Subscription> polling = Collections.synchronizedSet(new HashSet<>());
    private final ScheduledExecutorService heartBeatService;
    private ScheduledFuture<?> heartBeatTask;
    private final AtomicInteger subscriptIdSequence;
    private static final Duration DEFAULT_HEART_BEAT_INTERVAL = Duration.ofSeconds(30);

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
            this.subscriptIdSequence = new AtomicInteger(0);
        } catch (URISyntaxException e) {
            logger.error("Invalid URL!", e);
            throw new StompException("Error creating StompClient", e);
        }
    }

    @Override
    public void onConnected(Transport transport) {
        logger.info("Connected with server {}", transport);
        transport.send(Stomp.connect(transport.host(), credentials, protocols, DEFAULT_HEART_BEAT_INTERVAL));
    }

    private Optional<Subscription> findSubscription(Optional<String> subscriptionId,
                                                    Optional<String> destination,
                                                    Set<Subscription> subscriptions) {
        /*
         * 1. Need to filter all ids that are non-numbers. ActiveMQ Stomp V1.0 sends as
         * subscription id "/subscription/<topic>". So, we need to filter out the value
         * and use the destination header to follow the specification.
         */
        return subscriptionId.filter(id -> id.matches("\\d+"))
                             .map(Integer::parseInt)
                             .flatMap(id -> subscriptions.stream()
                                                         .filter(subs -> subs.id() == id)
                                                         .findFirst())
                             .or(() -> subscriptions.stream()
                                                    .filter(subs -> subs.topic()
                                                                        .equals(destination.orElseThrow(() -> new IllegalStateException("No destination found in message"))))
                                                    .findFirst());
    }

    private Optional<Subscription> findPollingSubscription(Optional<String> subscriptionId,
                                                           Optional<String> destination) {
        return findSubscription(subscriptionId, destination, polling);
    }

    private Optional<Subscription> findConsumerSubscription(Optional<String> subscriptionId,
                                                            Optional<String> destination) {
        return findSubscription(subscriptionId, destination, consumers.keySet());
    }

    @Override
    public void onMessage(Message message) {
        logger.info("Received message: {}", message);
        switch (message.command()) {
            case CONNECTED:
                setupConnection(message);
                break;
            case MESSAGE:
                consumeMessage(message);
                break;
            default:
                break;
        }
    }

    private void consumeMessage(Message message) {
        var subscription = findConsumerSubscription(message.headers().get(Header.SUBSCRIPTION),
                                                    message.headers().get(Header.DESTINATION));

        if (subscription.isPresent()) {
            consumers.get(subscription.get()).accept(message.payload());
        } else {
            subscription = findPollingSubscription(message.headers().get(Header.SUBSCRIPTION),
                                                   message.headers().get(Header.DESTINATION));
            if (subscription.isEmpty()) {
                receivedMessages.computeIfAbsent(subscription.get(), k -> new LinkedList<>())
                                .add(message);
            } else {
                logger.warn("No subscription found for message: {}", message);
            }
        }

        selectedProtocol.get().onMessage(message, session, transport);
    }

    @Override
    public void onError(Message message) {
        logger.error("Error message: {}", message);
    }

    public StompClient connect() {
        logger.info("Connecting with server {}", transport);
        transport.connect();
        logger.info("Waiting for client connection");
        try {
            connectedLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("Client connected");
        return this;
    }

    public Subscription subscribe(String topic) {
        var subscription = new Subscription(topic, subscriptIdSequence.incrementAndGet());
        this.selectedProtocol.get().subscribe(subscription, session, this.transport);
        return subscription;
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

    public Subscription subscribe(String topic, Consumer<String> consumer) {
        var subscription = new Subscription(topic, subscriptIdSequence.incrementAndGet());
        this.consumers.put(subscription, consumer);
        this.selectedProtocol.get().subscribe(subscription, this.session, this.transport);
        return subscription;
    }

    public void sendPlain(String destination, String content, String contentType) {
        this.selectedProtocol.get().send(destination, content, contentType, this.session, this.transport);
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

    public StompClient unsubscribe(Subscription subscription) {
        this.selectedProtocol.get().unsubscribe(subscription, transport);
        this.consumers.remove(subscription);
        return this;
    }

    public StompClient unsubscribe(String topic) {
        this.consumers.entrySet()
                      .stream()
                      .map(Map.Entry::getKey)
                      .filter(subscription -> subscription.topic().equals(topic))
                      .toList() // avoid concurrent exception
                      .forEach(subscription -> {
                          this.selectedProtocol.get().unsubscribe(subscription, transport);
                          this.consumers.remove(subscription);
                      });
        return this;
    }

    private void setupConnection(Message message) {
        logger.info("Connected with server {}", message);
        session = message.headers().get(Header.SESSION);
        selectedProtocol.set(Stomp.getProtocol(message.headers().version(),
                                               protocols));
        if (selectedProtocol.get().hasHeartBeat()) {
            logger.info("Heart beat is enabled");
            logger.info("Heart beat interval: {}", message.headers().get(Header.HEART_BEAT));
            message.headers()
                   .get(Header.HEART_BEAT)
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
