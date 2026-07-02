package dev.vepo.stomp4j.client.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.SendOptions;
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.StompDelivery;
import dev.vepo.stomp4j.client.StompReceipt;
import dev.vepo.stomp4j.client.StompTransaction;
import dev.vepo.stomp4j.client.SubscribeOptions;
import dev.vepo.stomp4j.client.Subscription;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.client.internal.transport.TransportFactory;
import dev.vepo.stomp4j.client.protocol.SendParameters;
import dev.vepo.stomp4j.client.protocol.Stomp;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Knowing:</b> Selected STOMP protocol, transport handle, and connection
 * lifecycle for one connected client.</li>
 * <li><b>Doing:</b> Orchestrate transport connect, CONNECT negotiation,
 * subscribe/send/unsubscribe, and graceful disconnect.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> {@link Transport}, {@link Stomp},
 * {@link SubscriptionDispatcher}, {@link ReceiptTracker},
 * {@link ClientHeartbeat}
 * </p>
 * <p>
 * <b>Not responsible for:</b> Wire I/O and framing, inbound subscription
 * routing, receipt correlation, heart-beat scheduling.
 * </p>
 */
public class StompClientImpl implements StompClient {

    private class ConnectionListener implements TransportListener {

        @Override
        public void onConnected(Transport connectedTransport) {
            logger.info("Connected with server {}", connectedTransport);
            connectedTransport.send(Stomp.connect(connectedTransport.host(),
                                                  credentials,
                                                  protocols,
                                                  ClientHeartbeat.DEFAULT_HEART_BEAT_INTERVAL));
        }

        @Override
        public void onError(Message message) {
            logger.error("Error message: {}", message);
            var errorMessage = message.headers().get(Header.MESSAGE).orElse(message.body());
            connectionError.set(new StompException(errorMessage));
            connectedLatch.countDown();
        }

        @Override
        public void onMessage(Message message) {
            logger.info("Received message: {}", message);
            switch (message.command()) {
                case CONNECTED:
                    setupConnection(message);
                    break;
                case MESSAGE:
                    subscriptions.dispatch(message);
                    break;
                case RECEIPT:
                    receipts.completeReceipt(message);
                    break;
                case ERROR:
                    if (!receipts.failFromError(message)) {
                        onError(message);
                    }
                    break;
                case HEARTBEAT:
                    break;
                default:
                    break;
            }
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(StompClientImpl.class);
    private static final Duration DEFAULT_CLOSE_GRACE_PERIOD = Duration.ofMillis(500);

    private final CountDownLatch connectedLatch;
    private final Object lock = new Object();
    private final AtomicReference<Stomp> selectedProtocol = new AtomicReference<>();
    private final AtomicReference<StompException> connectionError = new AtomicReference<>();
    private final ReceiptTracker receipts = new ReceiptTracker();
    private final ScheduledExecutorService heartBeatService;
    private final ClientHeartbeat heartbeat;
    private final SubscriptionDispatcher subscriptions;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final SSLContext sslContext;

    private UserCredential credentials;
    private Transport transport;
    private Set<Stomp> protocols;
    private Optional<String> session;
    private TransportListener listener;

    public StompClientImpl(String url, UserCredential credentials, TransportType transportType, Set<Stomp> protocols, SSLContext sslContext) {
        try {
            this.protocols = protocols;
            this.sslContext = sslContext;
            this.heartBeatService = Executors.newSingleThreadScheduledExecutor();
            this.heartbeat = new ClientHeartbeat(heartBeatService);
            this.subscriptions = new SubscriptionDispatcher(heartbeat,
                                                            selectedProtocol::get,
                                                            () -> session,
                                                            () -> transport);
            this.listener = new ConnectionListener();
            var uri = new URI(url);
            if (Objects.isNull(transportType)) {
                this.transport = createTransport(uri, this.listener);
            } else {
                this.transport = createTransport(uri, this.listener, transportType);
            }
            this.session = Optional.empty();
            this.credentials = credentials;
            this.connectedLatch = new CountDownLatch(1);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
    }

    private StompException abortConnect(StompException failure) {
        logger.error("STOMP connect failed for transport {}", transport, failure);
        close();
        return failure;
    }

    void abortTransaction(String transactionId) {
        selectedProtocol.get().abortTransaction(transactionId, transport);
    }

    @Override
    public StompTransaction beginTransaction() {
        var transactionId = UUID.randomUUID().toString();
        selectedProtocol.get().beginTransaction(transactionId, transport);
        return new StompTransactionImpl(this, transactionId);
    }

    @Override
    public void close() {
        close(DEFAULT_CLOSE_GRACE_PERIOD);
    }

    @Override
    public void close(Duration gracePeriod) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        logger.info("Stopping Stomp client");
        heartbeat.stop();
        if (Objects.nonNull(selectedProtocol.get())) {
            gracefulDisconnect(gracePeriod);
        } else {
            transport.close();
        }
        receipts.failAllOnClose();
        heartbeat.shutdown();
        logger.info("Stomp client stopped");
        synchronized (lock) {
            lock.notify();
        }
    }

    void commitTransaction(String transactionId) {
        selectedProtocol.get().commitTransaction(transactionId, transport);
    }

    @Override
    public StompClient connect() {
        logger.info("Connecting with server {}", transport);
        try {
            transport.connect();
        } catch (StompException ex) {
            throw abortConnect(ex);
        }
        logger.info("Waiting for client connection");
        try {
            connectedLatch.await();
        } catch (InterruptedException ex) {
            throw abortConnect(new StompException("Connection interrupted", ex));
        }
        var error = connectionError.get();
        if (Objects.nonNull(error)) {
            throw abortConnect(error);
        }
        if (Objects.isNull(selectedProtocol.get())) {
            throw abortConnect(new StompException("Connection failed"));
        }
        logger.info("Client connected");
        return this;
    }

    private Transport createTransport(URI uri, TransportListener transportListener) {
        return createTransport(uri, transportListener, null);
    }

    private Transport createTransport(URI uri, TransportListener transportListener, TransportType transportType) {
        var scheme = resolveScheme(uri, transportType);
        if (Objects.isNull(scheme)) {
            throw new IllegalArgumentException("No transport found for protocol null");
        }
        var transportUri = scheme.equals(uri.getScheme()) ? uri : uriWithScheme(uri, scheme);
        return TransportFactory.create(transportUri, transportListener, sslContext);
    }

    private void gracefulDisconnect(Duration gracePeriod) {
        var receiptId = UUID.randomUUID().toString();
        var completion = receipts.register(receiptId);
        transport.send(MessageBuilder.builder(Command.DISCONNECT)
                                     .header(Header.RECEIPT, receiptId)
                                     .build());
        try {
            completion.get(gracePeriod.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            logger.debug("Graceful disconnect timed out or failed: {}", ex.getMessage());
        } finally {
            receipts.remove(receiptId, completion);
            transport.close();
        }
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

    private String resolveScheme(URI uri, TransportType transportType) {
        if (Objects.isNull(transportType)) {
            return uri.getScheme();
        }
        return switch (transportType) {
            case TCP -> "stomps".equals(uri.getScheme()) ? "stomps" : "stomp";
            case WEB_SOCKET -> "wss".equals(uri.getScheme()) ? "wss" : "ws";
        };
    }

    @Override
    public StompReceipt send(String destination, String body, SendOptions options) {
        Objects.requireNonNull(options, "options cannot be null");
        return sendWithParameters(destination, body, options, Optional.empty());
    }

    StompReceipt sendInTransaction(String transactionId, String destination, String body, SendOptions options) {
        Objects.requireNonNull(options, "options cannot be null");
        return sendWithParameters(destination, body, options, Optional.of(transactionId));
    }

    @Override
    public void sendPlain(String destination, String content, String contentType) {
        selectedProtocol.get().send(destination, content, contentType, session, transport);
    }

    private StompReceipt sendWithParameters(String destination,
                                            String body,
                                            SendOptions options,
                                            Optional<String> transactionId) {
        var receiptId = options.receipt() ? Optional.of(UUID.randomUUID().toString()) : Optional.<String>empty();
        var parameters = SendParameters.of(options.headers(), receiptId, transactionId);
        if (receiptId.isEmpty()) {
            selectedProtocol.get()
                            .send(destination, body, options.contentType(), session, transport, parameters);
            return new StompReceiptImpl("", CompletableFuture.completedFuture(null));
        }
        var completion = receipts.register(receiptId.get());
        selectedProtocol.get()
                        .send(destination, body, options.contentType(), session, transport, parameters);
        completion.orTimeout(options.receiptTimeout().toMillis(), TimeUnit.MILLISECONDS)
                  .whenComplete((ignored, error) -> receipts.remove(receiptId.get(), completion));
        return new StompReceiptImpl(receiptId.get(), completion);
    }

    private void setupConnection(Message message) {
        logger.info("Connected with server {}", message);
        session = message.headers().get(Header.SESSION);
        selectedProtocol.set(Stomp.getProtocol(message.headers().version(), protocols));
        heartbeat.negotiateAndStart(message, transport, selectedProtocol::get);
        connectedLatch.countDown();
        logger.info("Client connected");
    }

    @Override
    public Subscription subscribe(String topic) {
        return subscribe(topic, SubscribeOptions.defaults());
    }

    @Override
    public Subscription subscribe(String topic, AckMode ackMode, Consumer<StompDelivery> consumer) {
        return subscribe(topic, SubscribeOptions.builder().ackMode(ackMode).build(), consumer);
    }

    @Override
    public Subscription subscribe(String topic, Consumer<String> consumer) {
        return subscriptions.subscribe(topic, consumer);
    }

    @Override
    public Subscription subscribe(String topic, SubscribeOptions options) {
        return subscriptions.subscribe(topic, options);
    }

    @Override
    public Subscription subscribe(String topic, SubscribeOptions options, Consumer<StompDelivery> consumer) {
        return subscriptions.subscribe(topic, options, consumer);
    }

    @Override
    public StompClient unsubscribe(String topic) {
        subscriptions.unsubscribe(topic);
        return this;
    }

    @Override
    public StompClient unsubscribe(Subscription subscription) {
        subscriptions.unsubscribe(subscription);
        return this;
    }

    private URI uriWithScheme(URI uri, String scheme) {
        try {
            return new URI(scheme, uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid transport URI for scheme %s".formatted(scheme), ex);
        }
    }
}
