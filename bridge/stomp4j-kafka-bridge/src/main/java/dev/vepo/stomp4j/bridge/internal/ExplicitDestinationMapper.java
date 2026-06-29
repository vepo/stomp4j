package dev.vepo.stomp4j.bridge.internal;

import java.util.Map;
import java.util.Optional;

import dev.vepo.stomp4j.bridge.DestinationMapper;

public final class ExplicitDestinationMapper implements DestinationMapper {
    private final Map<String, String> stompToKafka;

    public ExplicitDestinationMapper(Map<String, String> stompToKafka) {
        this.stompToKafka = Map.copyOf(stompToKafka);
    }

    @Override
    public Optional<String> toKafkaTopic(String stompDestination) {
        return Optional.ofNullable(stompToKafka.get(stompDestination));
    }
}
