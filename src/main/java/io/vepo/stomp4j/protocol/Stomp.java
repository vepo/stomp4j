package io.vepo.stomp4j.protocol;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.vepo.stomp4j.UserCredential;
//import io.vepo.stomp4j.port.WebSocketPort;
import io.vepo.stomp4j.protocol.v1_0.Stomp1_0;
import io.vepo.stomp4j.protocol.v1_1.Stomp1_1;
import io.vepo.stomp4j.protocol.v1_2.Stomp1_2;

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

    public static String connect(String host, UserCredential credentials, Set<Stomp> versions) {
        var builder = MessageBuilder.builder(Command.CONNECT)
                                    .header(Header.ACCEPT_VERSION, acceptedVersions(versions))
                                    .header(Header.HOST, host)
                                    .header(Header.HEART_BEAT, "0,0");

        if (Objects.nonNull(credentials)) {
            builder.header(Header.LOGIN, credentials.username())
                   .header(Header.PASSCODE, credentials.password());
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

    public abstract void subscribe(String topic, Optional<String> session, Transport transport);

    public String heartBeatMessage() {
        return Message.NEW_LINE + Message.END;
    }

    public abstract boolean shouldAcknowledge();

    public abstract String acknowledge(Message message);

    // public abstract void unsubscribe(String topic, WebSocketPort webSocket);
}
