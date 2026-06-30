package dev.vepo.stomp4j.commons.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public class MessageBuilder {
    public static MessageBuilder builder(Command command) {
        return new MessageBuilder(command);
    }

    private final Command command;
    private final Map<String, String> headers;
    private String body;

    public MessageBuilder(Command command) {
        this.command = command;
        this.headers = new TreeMap<>();
        this.body = null;
    }

    public MessageBuilder body(String body) {
        this.body = body;
        return this;
    }

    public Message build() {
        return new Message(command, new Headers(headers), body);
    }

    public MessageBuilder header(Header key, String value) {
        this.headers.put(key.value(), value);
        return this;
    }

    public MessageBuilder header(String key, String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        this.headers.put(key, value);
        return this;
    }

    public MessageBuilder headerIfPresent(Header key, Optional<String> value) {
        value.ifPresent(v -> this.headers.put(key.value(), v));
        return this;
    }

}
