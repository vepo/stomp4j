package dev.vepo.stomp4j.commons.protocol;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A simple class to represent the STOMP headers.
 */
public class Headers {
    public static class HeadersBuilder {
        private final Map<String, String> headers;

        private HeadersBuilder() {
            this.headers = new TreeMap<>();
        }

        public Headers build() {
            return new Headers(this.headers);
        }

        public HeadersBuilder with(Header key, String value) {
            Objects.requireNonNull(key);
            return with(key.value(), value);
        }

        public HeadersBuilder with(String key, String value) {
            Objects.requireNonNull(key);
            this.headers.put(key, value);
            return this;
        }
    }

    public static HeadersBuilder builder() {
        return new HeadersBuilder();
    }

    private final Map<String, String> headers;

    public Headers() {
        this(new HashMap<>());
    }

    public Headers(Map<String, String> headers) {
        this.headers = headers;
    }

    public void add(String key, String value) {
        headers.put(key, value);
    }

    public Map<String, String> asMap() {
        return Map.copyOf(headers);
    }

    public Optional<String> destination() {
        String destination = headers.get("destination");
        if (destination == null) {
            String receiptId = headers.get("receipt-id");
            if (receiptId == null || !receiptId.startsWith("receipt_")) {
                return Optional.empty();
            }
            return Optional.of(receiptId.replace("receipt_", ""));
        }

        return Optional.of(destination);
    }

    public Optional<String> get(Header header) {
        return get(header.value());
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(headers.get(key));
    }

    @Override
    public String toString() {
        return String.format("StompHeaders{headers=%s}", headers);
    }

    public String version() {
        return headers.getOrDefault("version", "1.0");
    }

    public void write(StringBuilder builder, Command command) {
        headers.forEach((key, value) -> builder.append(key)
                                               .append(Message.DELIMITER)
                                               .append(HeaderCodec.encodeValue(command, value))
                                               .append(Message.NEW_LINE));
    }
}
