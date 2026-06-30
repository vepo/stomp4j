package dev.vepo.stomp4j.commons.protocol;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Objects;

public record Message(Command command, Headers headers, String body) {

    static final String DELIMITER = ":";
    static final String END = "\u0000";
    public static final String NEW_LINE = "\n";
    public static final Message HEARTBEAT = new Message(Command.HEARTBEAT);

    public Message(Command command, Headers headers) {
        this(command, headers, "");
    }

    public Message(Command command) {
        this(command, new Headers(), "");
    }

    public static Message readMessage(String content) {
        return FrameDecoder.decode(content);
    }

    public static String formatted(String message) {
        return message.replace(Message.END, "^@");
    }

    public String encode() {
        if (command == Command.HEARTBEAT) {
            return NEW_LINE;
        }
        var encodedHeaders = new HashMap<>(headers.asMap());
        var frameBody = Objects.nonNull(body) ? body : "";
        if (!frameBody.isEmpty() && !encodedHeaders.containsKey(Header.CONTENT_LENGTH.value())) {
            encodedHeaders.put(Header.CONTENT_LENGTH.value(),
                               Integer.toString(frameBody.getBytes(StandardCharsets.UTF_8).length));
        }
        var builder = new StringBuilder();
        builder.append(command.name())
               .append(Message.NEW_LINE);
        new Headers(encodedHeaders).write(builder, command);
        builder.append(Message.NEW_LINE);
        if (!frameBody.isEmpty()) {
            builder.append(frameBody);
        }
        return builder.append(Message.END)
                      .toString();
    }
}
