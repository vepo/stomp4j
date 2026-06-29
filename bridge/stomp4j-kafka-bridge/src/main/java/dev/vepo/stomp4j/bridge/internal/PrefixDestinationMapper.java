package dev.vepo.stomp4j.bridge.internal;

import java.util.Optional;

import dev.vepo.stomp4j.bridge.DestinationMapper;

public final class PrefixDestinationMapper implements DestinationMapper {
    private final String prefix;

    public PrefixDestinationMapper(String prefix) {
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("prefix cannot be empty");
        }
        this.prefix = prefix;
    }

    @Override
    public Optional<String> toKafkaTopic(String stompDestination) {
        if (!stompDestination.startsWith(prefix)) {
            return Optional.empty();
        }
        var remainder = stompDestination.substring(prefix.length());
        if (remainder.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(remainder.replace('/', '.'));
    }
}
