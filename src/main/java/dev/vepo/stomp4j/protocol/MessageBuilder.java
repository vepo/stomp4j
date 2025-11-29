package dev.vepo.stomp4j.protocol;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public class MessageBuilder {
    private final Command command;
    private Map<Header, String> headers;
    private String body;

    public MessageBuilder(Command command) {
        this.command = command;
        this.headers = new TreeMap<>();
        this.body = null;
    }

    public static MessageBuilder builder(Command command) {
        return new MessageBuilder(command);
    }

    public MessageBuilder header(Header key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public MessageBuilder body(String body) {
        this.body = body;
        return this;
    }

    public MessageBuilder headerIfPresent(Header key, Optional<String> value) {
        value.ifPresent(v -> this.headers.put(key, v));
        return this;
    }

    public String build() {
        var builder = new StringBuilder();
        builder.append(command.name())
               .append(Message.NEW_LINE);
        headers.forEach((key, value) -> builder.append(key.value())
                                               .append(Message.DELIMITER)
                                               .append(value)
                                               .append(Message.NEW_LINE));
        builder.append(Message.NEW_LINE);
        if (Objects.nonNull(body)) {
            builder.append(body);
        }
            
        return builder.append(Message.END)
                      .toString();
    }

}
