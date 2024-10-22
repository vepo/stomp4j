package dev.vepo.stomp4j.protocol;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A simple class to represent the STOMP headers.
 */
public class Headers {
    private final Map<String, String> headers = new HashMap<>();

    public void add(String key, String value) {
        headers.put(key, value);
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

    @Override
    public String toString() {
        return String.format("StompHeaders{headers=%s}", headers);
    }

    public String version() {
        return headers.getOrDefault("version", "1.0");
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(headers.get(key));
    }

    public Optional<String> get(Header header) {
        return get(header.value());
    }
}
