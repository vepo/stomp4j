package dev.vepo.stomp4j.client.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.client.SendOptions;
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;
import dev.vepo.stomp4j.client.tests.infra.StompActiveMqContainer;
import dev.vepo.stomp4j.client.tests.infra.StompContainer;
import dev.vepo.stomp4j.client.tests.infra.TestDestinations;

@Tag("integration")
@ExtendWith(StompContainer.class)
@Execution(ExecutionMode.CONCURRENT)
class StompClientReceiptTest {

    @Test
    @DisplayName("Should complete receipt future when broker acknowledges send")
    void shouldReceiveReceiptForSend(StompActiveMqContainer stomp) throws Exception {
        var destination = TestDestinations.uniqueQueue("receipt");
        var credentials = new UserCredential(stomp.username(), stomp.password());

        try (var client = StompClient.create(stomp.tcpUrl(), credentials, Set.of(new Stomp1_2()))) {
            client.connect();
            var receipt = client.send(destination,
                                      "payload",
                                      SendOptions.builder().receipt(true).receiptTimeout(Duration.ofSeconds(10)).build());
            receipt.completion().get();
            assertThat(receipt.receiptId()).isNotBlank();
        }
    }
}
