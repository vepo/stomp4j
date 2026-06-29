package dev.vepo.stomp4j.client.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
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

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.client.Subscription;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.client.internal.transport.SecureTcpTransport;
import dev.vepo.stomp4j.client.internal.transport.SecureWebSocketTransport;
import dev.vepo.stomp4j.client.internal.transport.TcpTransport;
import dev.vepo.stomp4j.client.internal.transport.TransportFactory;
import dev.vepo.stomp4j.client.internal.transport.WebSocketTransport;
import dev.vepo.stomp4j.client.protocol.Stomp;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;

public class StompClientImpl implements StompClient {

    private class SubscriptionImpl implements Subscription {
        private final String topic;
        private final int id;

        private SubscriptionImpl(String topic, int id) {
            this.topic = topic;
            this.id = id;
        }

        @Override
        public String topic() {
            return this.topic;
        }

        @Override
        public int id() {
            return this.id;
        }

        @Override
        public boolean hasData() {
            Queue<Message> messageQueue = receivedMessages.get(SubscriptionImpl.this);
            return Objects.nonNull(messageQueue) && !messageQueue.isEmpty();
        }

        @Override
        public List<String> poll() {
            Queue<Message> messageQueue = receivedMessages.get(SubscriptionImpl.this);
            List<String> messages = new ArrayList<>(messageQueue.size());
            while(!messageQueue.isEmpty()) {
                messages.add(messageQueue.poll().body());
            }
            logger.debug("polled messages! {}", messages);
            return messages;
        }

        @Override
        public int hashCode() {
            return Objects.hash(topic, id);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (Objects.isNull(obj) || obj.getClass() != getClass()) {
                return false;
            } else {
                var other = (SubscriptionImpl) obj;
                return id == other.id && Objects.equals(topic, other.topic);
            }
        }

        @Override
        public String toString() {
            return "Subscription[topic=%s, id=%s]".formatted(topic, id);
        }
    }

    private class ConnectionListener implements TransportListener {

        @Override
        public void onConnected(Transport transport) {
            logger.info("Connected with server {}", transport);
            transport.send(Stomp.connect(transport.host(), credentials, protocols, DEFAULT_HEART_BEAT_INTERVAL));
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
                case ERROR:
                    onError(message);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onError(Message message) {
            logger.error("Error message: {}", message);
            var errorMessage = message.headers().get(Header.MESSAGE).orElse(message.body());
            connectionError.set(new StompException(errorMessage));
            connectedLatch.countDown();
        }

    }

    private static final Logger logger = LoggerFactory.getLogger(StompClientImpl.class);
    private static final Duration DEFAULT_HEART_BEAT_INTERVAL = Duration.ofSeconds(30);
    private final CountDownLatch connectedLatch;
    private final Object lock = new Object();
    private final AtomicReference<Stomp> selectedProtocol = new AtomicReference<>();
    private final AtomicReference<StompException> connectionError = new AtomicReference<>();
    private UserCredential credentials;
    private Transport transport;
    private final Map<Subscription, Consumer<String>> consumers = new HashMap<>();
    private final Map<Subscription, Queue<Message>> receivedMessages = Collections.synchronizedMap(new HashMap<>());
    private final Set<Subscription> polling = Collections.synchronizedSet(new HashSet<>());
    private final ScheduledExecutorService heartBeatService;
    private ScheduledFuture<?> heartBeatTask;

    private final AtomicInteger subscriptIdSequence;
    private Set<Stomp> protocols;
    private Optional<String> session;

    private final SSLContext sslContext;
    private TransportListener listener;

    public StompClientImpl(String url, UserCredential credentials, TransportType transportType, Set<Stomp> protocols, SSLContext sslContext) {
        try {
            this.protocols = protocols;
            this.sslContext = sslContext;
            this.listener = new ConnectionListener();
            var uri = new URI(url);
            if (Objects.isNull(transportType)) {
                this.transport = createTransport(uri, this.listener);
            } else {
                this.transport = switch (transportType) {
                    case WEB_SOCKET -> createWebSocketTransport(uri, this.listener);
                    case TCP -> createTcpTransport(uri, this.listener);
                };
            }
            this.session = Optional.empty();
            this.credentials = credentials;
            this.heartBeatService = Executors.newSingleThreadScheduledExecutor();
            this.heartBeatTask = null;
            this.connectedLatch = new CountDownLatch(1);
            this.subscriptIdSequence = new AtomicInteger(0);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
    }

    @Override
    public StompClient connect() {
        logger.info("Connecting with server {}", transport);
        transport.connect();
        logger.info("Waiting for client connection");
        try {
            connectedLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var error = connectionError.get();
        if (Objects.nonNull(error)) {
            throw error;
        }
        if (Objects.isNull(selectedProtocol.get())) {
            throw new StompException("Connection failed");
        }
        logger.info("Client connected");
        return this;
    }

    @Override
    public Subscription subscribe(String topic) {
        var subscriptionId = subscriptIdSequence.incrementAndGet();
        var subscription = new SubscriptionImpl(topic, subscriptionId);
        this.polling.add(subscription);
        this.selectedProtocol.get().subscribe(subscription, session, this.transport);
        return subscription;
    }

    @Override
    public Subscription subscribe(String topic, Consumer<String> consumer) {
        var subscriptionId = subscriptIdSequence.incrementAndGet();
        var subscription = new SubscriptionImpl(topic, subscriptionId);
        this.consumers.put(subscription, consumer);
        this.selectedProtocol.get().subscribe(subscription, this.session, this.transport);
        return subscription;
    }

    @Override
    public void join() {
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void sendPlain(String destination, String content, String contentType) {
        this.selectedProtocol.get().send(destination, content, contentType, this.session, this.transport);
    }

    @Override
    public StompClient unsubscribe(Subscription subscription) {
        this.selectedProtocol.get().unsubscribe(subscription, transport);
        this.consumers.remove(subscription);
        return this;
    }

    @Override
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

    @Override
    public void close() {
        logger.info("Stopping Stomp client");
        if (Objects.nonNull(selectedProtocol.get())) {
            transport.send(MessageBuilder.builder(Command.DISCONNECT).build());
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        transport.close();
        logger.info("Stopping heartbeat service");
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

    private void consumeMessage(Message message) {
        var subscription = findConsumerSubscription(message.headers().get(Header.SUBSCRIPTION),
                                                    message.headers().get(Header.DESTINATION));

        if (subscription.isPresent()) {
            consumers.get(subscription.get()).accept(message.body());
            logger.debug("Consumer found! {}", subscription);
        } else {
            subscription = findPollingSubscription(message.headers().get(Header.SUBSCRIPTION),
                                                   message.headers().get(Header.DESTINATION));
            logger.debug("Subscription found! {}", subscription);
            if (!subscription.isEmpty()) {
                receivedMessages.computeIfAbsent(subscription.get(), k -> new LinkedList<>())
                                .add(message);
            } else {
                logger.warn("No subscription found for message: {}", message);
            }
        }

        selectedProtocol.get().onMessage(message, session, transport);
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

    private Transport createTransport(URI uri, TransportListener transportListener) {
        var scheme = uri.getScheme();
        if (Objects.isNull(scheme)) {
            throw new IllegalArgumentException("No transport found for protocol null");
        }
        return switch (scheme) {
            case "stomps" -> createTcpTransport(uri, transportListener);
            case "wss" -> createWebSocketTransport(uri, transportListener);
            default -> TransportFactory.create(uri, transportListener);
        };
    }

    private Transport createTcpTransport(URI uri, TransportListener transportListener) {
        if ("stomps".equals(uri.getScheme())) {
            return Objects.isNull(sslContext)
                    ? new SecureTcpTransport(uri, transportListener)
                    : new SecureTcpTransport(uri, transportListener, sslContext);
        }
        return new TcpTransport(uri, transportListener);
    }

    private Transport createWebSocketTransport(URI uri, TransportListener transportListener) {
        if ("wss".equals(uri.getScheme())) {
            return Objects.isNull(sslContext)
                    ? new SecureWebSocketTransport(uri, transportListener)
                    : new SecureWebSocketTransport(uri, transportListener, sslContext);
        }
        return new WebSocketTransport(uri, transportListener);
    }
}
