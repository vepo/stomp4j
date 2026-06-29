package dev.vepo.stomp4j.server.session;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuffer;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;
import dev.vepo.stomp4j.server.OutboundChannel;
import dev.vepo.stomp4j.server.StompSession;
import dev.vepo.stomp4j.server.SubscriberAckListener;
import dev.vepo.stomp4j.server.auth.Credentials;
import dev.vepo.stomp4j.server.channels.ChannelListener;

public class Session implements StompSession {
    private record HeartbeatNegotiation(long serverMs, long clientMs) {}

    private record PendingMessage(Message message, Subscription subscription, Optional<SubscriberAckListener> listener) {}

    private record Subscription(String topic, String id, Optional<String> ackMode) {}

    private static final Logger logger = LoggerFactory.getLogger(Session.class);

    private static final double HEARTBEAT_TOLERANCE = 2.0;
    private final OutboundChannel channel;
    private final MessageBuffer buffer;
    private final ChannelListener listener;
    private final SessionConfig config;
    private final SessionCloser closer;
    private final ScheduledExecutorService heartbeatExecutor;
    private final Set<Subscription> subscriptions;
    private final Map<String, PendingMessage> pendingMessages;
    private final AtomicLong messageIdSequence;

    private final AtomicLong lastReadTime;
    private Status status;
    private String negotiatedVersion;
    private Optional<String> login;
    private Optional<String> sessionId;
    private long serverHeartbeatMs;
    private long clientHeartbeatMs;
    private ScheduledFuture<?> heartbeatTask;

    private ScheduledFuture<?> watchdogTask;

    public Session(OutboundChannel channel,
                   ChannelListener listener,
                   SessionConfig config,
                   SessionCloser closer,
                   ScheduledExecutorService heartbeatExecutor) {
        this.channel = channel;
        this.buffer = new MessageBuffer();
        this.listener = listener;
        this.config = config;
        this.closer = closer;
        this.heartbeatExecutor = heartbeatExecutor;
        this.subscriptions = new HashSet<>();
        this.pendingMessages = new HashMap<>();
        this.messageIdSequence = new AtomicLong(0);
        this.lastReadTime = new AtomicLong(System.currentTimeMillis());
        this.status = Status.STARTED;
        this.negotiatedVersion = "1.2";
        this.login = Optional.empty();
        this.sessionId = Optional.empty();
    }

    private void acknowledgePendingMessage(Message message) {
        resolveAckMessageId(message).ifPresent(messageId -> {
            var pending = pendingMessages.remove(messageId);
            if (Objects.nonNull(pending)) {
                pending.listener().ifPresent(listener -> listener.onAck(messageId, this));
            }
        });
    }

    private boolean authenticate(Message message) {
        return config.authenticator()
                     .map(authenticator -> {
                         var username = message.headers().get(Header.LOGIN).orElse("");
                         var password = message.headers().get(Header.PASSCODE).orElse("");
                         return authenticator.authenticate(new Credentials(username, password));
                     })
                     .orElse(true);
    }

    private void checkClientHeartbeat() {
        if (status != Status.CONNECTED) {
            return;
        }
        var silentTime = System.currentTimeMillis() - lastReadTime.get();
        if (silentTime > clientHeartbeatMs * HEARTBEAT_TOLERANCE) {
            logger.info("Closing session due to missed client heartbeat");
            closeSession();
        }
    }

    private void closeSession() {
        endSession();
    }

    private void deliverMessage(Message message, Subscription subscription) {
        deliverMessage(message, subscription, Optional.empty());
    }

    private void deliverMessage(Message message, Subscription subscription, Optional<SubscriberAckListener> listener) {
        var messageId = Long.toString(messageIdSequence.incrementAndGet());
        var builder = MessageBuilder.builder(Command.MESSAGE)
                                    .header(Header.SUBSCRIPTION, subscription.id())
                                    .header(Header.MESSAGE_ID, messageId)
                                    .header(Header.DESTINATION, subscription.topic())
                                    .header(Header.CONTENT_TYPE,
                                            message.headers().get(Header.CONTENT_TYPE).orElse("text/plain"))
                                    .body(message.body());
        subscription.ackMode().ifPresent(ack -> builder.header(Header.ACK, ack));
        var outbound = builder.build();
        channel.send(outbound);
        subscription.ackMode()
                    .filter(ack -> ack.equals("client") || ack.equals("client-individual"))
                    .ifPresent(ack -> pendingMessages.put(messageId, new PendingMessage(message, subscription, listener)));
    }

    private void endSession() {
        if (status == Status.END) {
            return;
        }
        status = Status.END;
        stopHeartbeatTasks();
        listener.sessionDisconnected(this);
        closer.close(this);
    }

    public void handle(Message message) {
        handle(message, Optional.empty());
    }

    public void handle(Message message, Optional<SubscriberAckListener> listener) {
        if (message.command() != Command.SEND) {
            return;
        }
        var destination = message.headers().destination().orElseThrow();
        subscriptions.stream()
                     .filter(subscription -> subscription.topic().equals(destination))
                     .forEach(subscription -> deliverMessage(message, subscription, listener));
    }

    @Override
    public Optional<String> login() {
        return login;
    }

    private boolean matchesUnsubscribe(Subscription subscription,
                                       Optional<String> id,
                                       Optional<String> destination) {
        return id.map(value -> value.equals(subscription.id()))
                 .orElseGet(() -> destination.map(value -> value.equals(subscription.topic()))
                                             .orElse(false));
    }

    private void negativeAcknowledgePendingMessage(Message message) {
        resolveAckMessageId(message).ifPresent(messageId -> {
            var pending = pendingMessages.remove(messageId);
            if (Objects.nonNull(pending)) {
                pending.listener().ifPresent(listener -> listener.onNack(messageId, this));
                redeliver(messageId, pending);
            }
        });
    }

    private HeartbeatNegotiation negotiateHeartbeat(String heartBeatHeader) {
        var parts = heartBeatHeader.split(",");
        var clientSend = parts.length > 0 ? parseHeartbeat(parts[0]) : 0L;
        var clientReceive = parts.length > 1 ? parseHeartbeat(parts[1]) : 0L;
        var desired = config.heartbeatInterval().toMillis();
        var serverSend = desired > 0 && clientReceive > 0 ? desired : 0L;
        var serverReceive = desired > 0 && clientSend > 0 ? desired : 0L;
        return new HeartbeatNegotiation(serverSend, serverReceive);
    }

    private String negotiateVersion(String acceptVersion) {
        var clientVersions = List.of(acceptVersion.split(","));
        return config.supportedVersions()
                     .stream()
                     .filter(clientVersions::contains)
                     .findFirst()
                     .orElseThrow(() -> new IllegalStateException("No supported STOMP version in common"));
    }

    public void offer(byte[] data, int length) {
        if (length <= 0) {
            return;
        }
        lastReadTime.set(System.currentTimeMillis());
        if (buffer.append(new String(data, 0, length))) {
            do {
                process(buffer.message());
            } while (buffer.hasMessage());
        }
    }

    public void offerHeartbeat() {
        lastReadTime.set(System.currentTimeMillis());
    }

    @Override
    public OutboundChannel outboundChannel() {
        return channel;
    }

    private long parseHeartbeat(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private void process(Message message) {
        if (message.command() == Command.HEARTBEAT) {
            return;
        }
        logger.debug("Processing message {}", message);
        switch (status) {
            case STARTED -> processStarted(message);
            case CONNECTED -> processConnected(message);
            case END -> logger.debug("Ignoring message on ended session: {}", message);
        }
    }

    private void processConnected(Message message) {
        switch (message.command()) {
            case SEND -> listener.inboundMessageReceived(this, message);
            case SUBSCRIBE -> {
                var topic = message.headers().destination().orElse("");
                var id = message.headers().get(Header.ID).orElse("0");
                var ackMode = message.headers().get(Header.ACK);
                if (listener.subscriptionRequested(this, topic)) {
                    subscriptions.add(new Subscription(topic, id, ackMode));
                }
            }
            case UNSUBSCRIBE -> {
                var id = message.headers().get(Header.ID);
                var destination = message.headers().destination();
                subscriptions.removeIf(subscription -> matchesUnsubscribe(subscription, id, destination));
            }
            case ACK -> acknowledgePendingMessage(message);
            case NACK -> negativeAcknowledgePendingMessage(message);
            case DISCONNECT -> endSession();
            default -> logger.warn("Command not implemented! {}", message);
        }
    }

    private void processStarted(Message message) {
        if (message.command() != Command.CONNECT) {
            sendError("Expected CONNECT frame");
            closeSession();
            return;
        }
        login = message.headers().get(Header.LOGIN);
        if (!authenticate(message)) {
            sendError("Authentication failed");
            closeSession();
            return;
        }
        try {
            negotiatedVersion = negotiateVersion(message.headers().get(Header.ACCEPT_VERSION).orElse("1.2"));
        } catch (IllegalStateException ex) {
            sendError("No supported STOMP version");
            closeSession();
            return;
        }
        var heartbeat = negotiateHeartbeat(message.headers().get(Header.HEART_BEAT).orElse("0,0"));
        serverHeartbeatMs = heartbeat.serverMs();
        clientHeartbeatMs = heartbeat.clientMs();
        sessionId = Optional.of(UUID.randomUUID().toString());

        var connectedBuilder = MessageBuilder.builder(Command.CONNECTED)
                                             .header(Header.VERSION, negotiatedVersion)
                                             .header(Header.SERVER, config.serverName())
                                             .header(Header.HEART_BEAT,
                                                     "%d,%d".formatted(serverHeartbeatMs, clientHeartbeatMs));
        if (!"1.0".equals(negotiatedVersion)) {
            connectedBuilder.header(Header.SESSION, sessionId.get());
        }
        channel.send(connectedBuilder.build());
        status = Status.CONNECTED;
        startHeartbeatTasks();
        listener.sessionConnected(this);
    }

    private void redeliver(String messageId) {
        var pending = pendingMessages.get(messageId);
        if (Objects.nonNull(pending)) {
            redeliver(messageId, pending);
        }
    }

    private void redeliver(String messageId, PendingMessage pending) {
        deliverMessage(pending.message(), pending.subscription(), pending.listener());
    }

    private Optional<String> resolveAckMessageId(Message message) {
        return message.headers()
                      .get(Header.ID)
                      .or(() -> message.headers().get(Header.MESSAGE_ID));
    }

    private void sendError(String message) {
        channel.send(MessageBuilder.builder(Command.ERROR)
                                   .header(Header.MESSAGE, message)
                                   .body(message)
                                   .build());
    }

    private void sendHeartbeatIfIdle() {
        if (status != Status.CONNECTED) {
            return;
        }
        var silentTime = System.currentTimeMillis() - lastReadTime.get();
        if (silentTime >= serverHeartbeatMs) {
            channel.send(Message.HEARTBEAT);
        }
    }

    private void startHeartbeatTasks() {
        if (serverHeartbeatMs > 0) {
            heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeatIfIdle,
                                                                  serverHeartbeatMs,
                                                                  serverHeartbeatMs,
                                                                  TimeUnit.MILLISECONDS);
        }
        if (clientHeartbeatMs > 0) {
            var timeout = Math.round(clientHeartbeatMs * HEARTBEAT_TOLERANCE);
            watchdogTask = heartbeatExecutor.scheduleAtFixedRate(this::checkClientHeartbeat,
                                                                 timeout,
                                                                 timeout,
                                                                 TimeUnit.MILLISECONDS);
        }
    }

    public Status status() {
        return status;
    }

    private void stopHeartbeatTasks() {
        if (Objects.nonNull(heartbeatTask)) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
        if (Objects.nonNull(watchdogTask)) {
            watchdogTask.cancel(false);
            watchdogTask = null;
        }
    }

    @Override
    public String toString() {
        return "Session[status=%s, version=%s]".formatted(status, negotiatedVersion);
    }

    @Override
    public String version() {
        return negotiatedVersion;
    }
}
