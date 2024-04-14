package io.vepo.stomp4j.protocol;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vepo.stomp4j.UserCredential;
import io.vepo.stomp4j.port.WebSocketPort;
import io.vepo.stomp4j.protocol.v1_1.Stomp1_1;
import io.vepo.stomp4j.protocol.v1_2.Stomp1_2;

public abstract class Stomp {
    final static String DELIMITER = ":";
    final static String END = "\u0000";
    final static String NEW_LINE = "\n";

    private static final Set<Stomp> IMPLS = Set.of(new Stomp1_1(), new Stomp1_2());

    public static String acceptedVersions() {
        return IMPLS.stream()
                    .map(Stomp::version)
                    .collect(Collectors.joining(","));
        // return "1.0,1.1,2.0";
    }

    public abstract boolean hasHeartBeat();

    public static Set<String> implementedVersions() {
        return IMPLS.stream()
                    .map(Stomp::version)
                    .collect(Collectors.toSet());
    }

    public static String connect(String host, UserCredential credentials) {
        var builder = MessageBuilder.builder(Command.CONNECT)
                                    .header(Header.ACCEPT_VERSION, acceptedVersions())
                                    .header(Header.HOST, host)
                                    .header(Header.HEART_BEAT, "0,0");

        if (Objects.nonNull(credentials)) {
            builder.header(Header.LOGIN, credentials.username())
                   .header(Header.PASSCODE, credentials.password());
        }

        return builder.build();
    }

    public static Message readMessage(String content) {
        String[] splitMessage = content.split(NEW_LINE);

        if (splitMessage.length == 0)
            throw new IllegalStateException("Did not received any message");

        String command = splitMessage[0];
        Headers stompHeaders = new Headers();
        String body = "";

        int cursor = 1;
        for (int i = cursor; i < splitMessage.length; i++) {
            // empty line
            if (splitMessage[i].isEmpty()) {
                cursor = i;
                break;
            } else {
                String[] header = splitMessage[i].split(DELIMITER);
                stompHeaders.add(header[0], header[1]);
            }
        }

        for (int i = cursor; i < splitMessage.length; i++) {
            body += splitMessage[i];
        }

        if (body.isEmpty())
            return new Message(Command.valueOf(command), stompHeaders);
        else
            return new Message(Command.valueOf(command), stompHeaders, body.replace(END, ""));
    }

    public static Stomp getProtocol(Object version) {
        return IMPLS.stream()
                    .filter(service -> service.version().equals(version))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Version not supported"));
    }

    public abstract String version();

    public abstract void handleMessage(Message message, StompEventListener listener);

    public abstract void subscribe(String topic, WebSocketPort webSocket);

    public String heartBeatMessage() {
        return NEW_LINE + END;
    }

    public abstract boolean shouldAcknowledge();

    public abstract String acknowledge(Message message);
}
