package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.vepo.stomp4j.client.SendOptions;
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;
import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.server.StompServer;

class StompClientTransactionTest {

    @Test
    @DisplayName("Client transaction should deliver SEND only after commit")
    @Timeout(value = 15)
    void shouldDeliverTransactionalSendAfterCommit() {
        var received = new LinkedList<String>();
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5530)
                                     .subscription(topic -> true)
                                     .handler(message -> received.add(message.body()))
                                     .start();
                var client = StompClient.create("stomp://localhost:5530", null, Set.of(new Stomp1_2()))) {
            client.connect();
            client.subscribe("tx-client-topic");
            try (var transaction = client.beginTransaction()) {
                transaction.send("tx-client-topic", "deferred", SendOptions.plainText());
                await().pollDelay(Duration.ofMillis(300)).until(() -> received.isEmpty());
                transaction.commit();
            }
            await().atMost(Duration.ofSeconds(5)).until(() -> received.contains("deferred"));
            assertThat(received).containsExactly("deferred");
        }
    }

    @Test
    @DisplayName("Client transaction should discard SEND on abort")
    @Timeout(value = 15)
    void shouldDiscardTransactionalSendOnAbort() {
        var received = new AtomicInteger();
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5531)
                                     .subscription(topic -> true)
                                     .handler(message -> received.incrementAndGet())
                                     .start();
                var client = StompClient.create("stomp://localhost:5531", null, Set.of(new Stomp1_2()))) {
            client.connect();
            try (var transaction = client.beginTransaction()) {
                transaction.send("tx-abort-topic", "aborted", SendOptions.plainText());
                transaction.abort();
            }
            await().pollDelay(Duration.ofMillis(500)).until(() -> received.get() == 0);
            assertThat(received.get()).isZero();
        }
    }
}
