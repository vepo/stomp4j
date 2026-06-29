package dev.vepo.stomp4j.bridge.internal;

import java.util.List;
import java.util.Optional;

import dev.vepo.stomp4j.bridge.DestinationMapper;

public final class CompositeDestinationMapper implements DestinationMapper {
    private final List<DestinationMapper> mappers;

    public CompositeDestinationMapper(List<DestinationMapper> mappers) {
        this.mappers = List.copyOf(mappers);
    }

    @Override
    public Optional<String> toKafkaTopic(String stompDestination) {
        for (var mapper : mappers) {
            var topic = mapper.toKafkaTopic(stompDestination);
            if (topic.isPresent()) {
                return topic;
            }
        }
        return Optional.empty();
    }
}
