package dev.vepo.stomp4j.client.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.client.protocol.Stomp;
import dev.vepo.stomp4j.client.protocol.v1_1.Stomp1_1;
import dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;
import dev.vepo.stomp4j.client.tests.infra.StompActiveMqContainer;
import dev.vepo.stomp4j.client.tests.infra.StompContainer;

@ExtendWith(StompContainer.class)
class StompClientAckTest {

    private static java.util.stream.Stream<Arguments> manualAckVersions() {
        return java.util.stream.Stream.of(Arguments.of(new Stomp1_1()),
                                          Arguments.of(new Stomp1_2()));
    }

    @ParameterizedTest
    @MethodSource("manualAckVersions")
    @DisplayName("Should allow manual acknowledgement of a delivery")
    void shouldAllowManualAcknowledgement(Stomp protocol, StompActiveMqContainer stomp) {
        var destination = "/queue/ack-" + UUID.randomUUID();
        var credentials = new UserCredential(stomp.username(), stomp.password());
        var processed = new AtomicBoolean(false);
        var acknowledged = new AtomicBoolean(false);

        try (var client = StompClient.create(stomp.tcpUrl(), credentials, Set.of(protocol))) {
            client.connect();
            client.subscribe(destination, AckMode.CLIENT_INDIVIDUAL, delivery -> {
                processed.set(true);
                assertThat(delivery.acknowledged()).isFalse();
                delivery.ack();
                acknowledged.set(delivery.acknowledged());
            });

            try (var producer = StompClient.create(stomp.tcpUrl(), credentials, Set.of(protocol))) {
                producer.connect();
                producer.sendPlain(destination, "payload", "text/plain");
            }

            await().atMost(Duration.ofSeconds(10)).until(() -> processed.get() && acknowledged.get());
        }
    }

    @ParameterizedTest
    @MethodSource("manualAckVersions")
    @DisplayName("Should allow manual negative acknowledgement of a delivery")
    void shouldAllowManualNegativeAcknowledgement(Stomp protocol, StompActiveMqContainer stomp) {
        var destination = "/queue/nack-" + UUID.randomUUID();
        var credentials = new UserCredential(stomp.username(), stomp.password());
        var nacked = new AtomicBoolean(false);

        try (var client = StompClient.create(stomp.tcpUrl(), credentials, Set.of(protocol))) {
            client.connect();
            client.subscribe(destination, AckMode.CLIENT_INDIVIDUAL, delivery -> {
                delivery.nack();
                nacked.set(delivery.acknowledged());
            });

            try (var producer = StompClient.create(stomp.tcpUrl(), credentials, Set.of(protocol))) {
                producer.connect();
                producer.sendPlain(destination, "payload", "text/plain");
            }

            await().atMost(Duration.ofSeconds(10)).until(nacked::get);
        }
    }
}
