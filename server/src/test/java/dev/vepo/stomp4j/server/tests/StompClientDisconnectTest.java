package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;
import dev.vepo.stomp4j.server.tests.infra.EmbeddedServerFixture;

@Execution(ExecutionMode.CONCURRENT)
class StompClientDisconnectTest {

    @Test
    @DisplayName("Client close should complete graceful DISCONNECT with receipt")
    @Timeout(value = 15)
    void shouldCloseGracefullyWithDisconnectReceipt() {
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .subscription(topic -> true)
                                                .handler(message -> {})
                                                .start()) {
            var client = StompClient.create(fixture.stompTcpUrl(), null, Set.of(new Stomp1_2()));
            client.connect();
            assertThatCode(() -> client.close(Duration.ofSeconds(5))).doesNotThrowAnyException();
        }
    }
}
