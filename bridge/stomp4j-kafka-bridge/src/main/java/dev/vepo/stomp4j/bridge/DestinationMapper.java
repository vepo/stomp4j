package dev.vepo.stomp4j.bridge;

import java.util.Optional;

public interface DestinationMapper {

    Optional<String> toKafkaTopic(String stompDestination);
}
