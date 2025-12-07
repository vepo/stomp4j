package dev.vepo.stomp4j.commons.protocol;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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

    public Message build() {
        return new Message(command,
                           new Headers(headers.entrySet()
                                              .stream()
                                              .collect(Collectors.toMap(entry -> entry.getKey().value(),
                                                                        Entry::getValue))),
                           body);
    }

}
