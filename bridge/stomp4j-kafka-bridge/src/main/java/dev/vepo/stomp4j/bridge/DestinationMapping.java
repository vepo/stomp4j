package dev.vepo.stomp4j.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.vepo.stomp4j.bridge.internal.CompositeDestinationMapper;
import dev.vepo.stomp4j.bridge.internal.ExplicitDestinationMapper;
import dev.vepo.stomp4j.bridge.internal.PrefixDestinationMapper;

public final class DestinationMapping {

    public static DestinationMapper composite(DestinationMapper first, DestinationMapper... rest) {
        Objects.requireNonNull(first, "first");
        var mappers = new ArrayList<DestinationMapper>();
        mappers.add(first);
        if (Objects.nonNull(rest)) {
            for (var mapper : rest) {
                mappers.add(Objects.requireNonNull(mapper, "mapper"));
            }
        }
        return new CompositeDestinationMapper(List.copyOf(mappers));
    }

    public static DestinationMapper explicit(Map<String, String> stompToKafka) {
        return new ExplicitDestinationMapper(stompToKafka);
    }

    public static DestinationMapper prefix(String prefix) {
        return new PrefixDestinationMapper(prefix);
    }

    private DestinationMapping() {}
}
