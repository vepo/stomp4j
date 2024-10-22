package dev.vepo.stomp4j.protocol;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dev.vepo.stomp4j.Subscription;
import dev.vepo.stomp4j.UserCredential;
//import dev.vepo.stomp4j.port.WebSocketPort;
import dev.vepo.stomp4j.protocol.v1_0.Stomp1_0;
import dev.vepo.stomp4j.protocol.v1_1.Stomp1_1;
import dev.vepo.stomp4j.protocol.v1_2.Stomp1_2;

public abstract class Stomp {

    public static final Set<Stomp> ALL_VERSIONS = Set.of(new Stomp1_0(),
                                                         new Stomp1_1(),
                                                         new Stomp1_2());

    public static String acceptedVersions(Set<Stomp> versions) {
        return versions.stream()
                       .map(Stomp::version)
                       .collect(Collectors.joining(","));
    }

    public abstract boolean hasHeartBeat();

    public static String connect(String host,
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

    public abstract String version();

    public abstract void onMessage(Message message, Optional<String> session, Transport transport);

    public abstract void subscribe(Subscription subscription, Optional<String> session, Transport transport);

    public String heartBeatMessage() {
        return Message.NEW_LINE;
    }

    public abstract void unsubscribe(Subscription subscription, Transport transport);
}
