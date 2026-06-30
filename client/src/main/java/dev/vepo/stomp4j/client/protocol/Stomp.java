package dev.vepo.stomp4j.client.protocol;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.Subscription;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;

public abstract class Stomp {

    public static final Set<Stomp> ALL_VERSIONS = ServiceLoader.load(Stomp.class)
                                                               .stream()
                                                               .map(provider -> provider.get())
                                                               .collect(Collectors.toSet());

    public static String acceptedVersions(Set<Stomp> versions) {
        return versions.stream()
                       .map(Stomp::version)
                       .collect(Collectors.joining(","));
    }

    protected static void applyCustomHeaders(MessageBuilder builder, Map<String, String> customHeaders) {
        customHeaders.forEach(builder::header);
    }

    protected static void applyTransaction(MessageBuilder builder, Optional<String> transactionId) {
        transactionId.ifPresent(id -> builder.header(Header.TRANSACTION, id));
    }

    public static Message connect(String host,
                                  UserCredential credentials,
                                  Set<Stomp> versions,
                                  Duration expectedHeartbeatFrequency) {
        var builder = MessageBuilder.builder(Command.CONNECT)
                                    .header(Header.ACCEPT_VERSION, acceptedVersions(versions))
                                    .header(Header.HOST, host)
                                    .header(Header.HEART_BEAT, String.format("%d,%d",
                                                                             expectedHeartbeatFrequency.toMillis(),
                                                                             expectedHeartbeatFrequency.toMillis()));

        if (Objects.nonNull(credentials)) {
            credentials.apply(builder);
        }

        return builder.build();
    }

    public static Stomp getProtocol(String version, Set<Stomp> versions) {
        return versions.stream()
                       .filter(service -> service.version().equals(version))
                       .findFirst()
                       .orElseThrow(() -> new IllegalArgumentException("Version not supported"));
    }

    public void abortTransaction(String transactionId, Transport transport) {
        transport.send(MessageBuilder.builder(Command.ABORT)
                                     .header(Header.TRANSACTION, transactionId)
                                     .build());
    }

    public abstract void acknowledge(Message message, Optional<String> session, Transport transport);

    public void acknowledge(Message message,
                            Optional<String> session,
                            Transport transport,
                            Optional<String> transactionId) {
        acknowledge(message, session, transport);
    }

    public void beginTransaction(String transactionId, Transport transport) {
        transport.send(MessageBuilder.builder(Command.BEGIN)
                                     .header(Header.TRANSACTION, transactionId)
                                     .build());
    }

    public void commitTransaction(String transactionId, Transport transport) {
        transport.send(MessageBuilder.builder(Command.COMMIT)
                                     .header(Header.TRANSACTION, transactionId)
                                     .build());
    }

    public abstract boolean hasHeartBeat();

    public Message heartBeatMessage() {
        return Message.HEARTBEAT;
    }

    public abstract void negativeAcknowledge(Message message, Optional<String> session, Transport transport);

    public void negativeAcknowledge(Message message,
                                    Optional<String> session,
                                    Transport transport,
                                    Optional<String> transactionId) {
        negativeAcknowledge(message, session, transport);
    }

    public abstract void onMessage(Message message, Optional<String> session, Transport transport);

    public abstract void send(String destination,
                              String content,
                              String contentType,
                              Optional<String> session,
                              Transport transport);

    public void send(String destination,
                     String content,
                     String contentType,
                     Optional<String> session,
                     Transport transport,
                     SendParameters parameters) {
        send(destination, content, contentType, session, transport);
    }

    public final void subscribe(Subscription subscription, Optional<String> session, Transport transport) {
        subscribe(subscription, session, transport, subscription.ackMode(), Map.of());
    }

    public abstract void subscribe(Subscription subscription,
                                   Optional<String> session,
                                   Transport transport,
                                   AckMode ackMode,
                                   Map<String, String> customHeaders);

    public abstract void unsubscribe(Subscription subscription, Transport transport);

    public abstract String version();
}
