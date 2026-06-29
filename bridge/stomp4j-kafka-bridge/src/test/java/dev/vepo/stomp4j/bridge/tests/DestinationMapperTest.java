package dev.vepo.stomp4j.bridge.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.bridge.DestinationMapping;

class DestinationMapperTest {

    @Test
    @DisplayName("Explicit mapper should override prefix mapping")
    void explicitMapperShouldOverridePrefix() {
        var mapper = DestinationMapping.composite(DestinationMapping.explicit(Map.of("/topic/legacy", "legacy-topic")),
                                                  DestinationMapping.prefix("/topic/"));

        assertThat(mapper.toKafkaTopic("/topic/legacy")).contains("legacy-topic");
        assertThat(mapper.toKafkaTopic("/topic/orders")).contains("orders");
    }

    @Test
    @DisplayName("Prefix mapper should strip topic prefix and replace slashes")
    void prefixMapperShouldMapDestinationToTopic() {
        var mapper = DestinationMapping.prefix("/topic/");

        assertThat(mapper.toKafkaTopic("/topic/orders")).contains("orders");
        assertThat(mapper.toKafkaTopic("/topic/chat/lobby")).contains("chat.lobby");
        assertThat(mapper.toKafkaTopic("/queue/orders")).isEmpty();
    }
}
