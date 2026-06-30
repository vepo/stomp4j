package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.client.SendOptions;
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;
import dev.vepo.stomp4j.server.tests.infra.EmbeddedServerFixture;
import dev.vepo.stomp4j.server.tests.infra.ServerTestSupport;
import dev.vepo.stomp4j.server.tests.infra.TestDestinations;

@Execution(ExecutionMode.CONCURRENT)
class StompClientTransactionTest {

    @Test
    @DisplayName("Client transaction should deliver SEND only after commit")
    @Timeout(value = 15)
    void shouldDeliverTransactionalSendAfterCommit() {
        var topic = TestDestinations.uniqueTopic("tx-client");
        var received = new LinkedList<String>();
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .subscription(t -> true)
                                                .handler(message -> received.add(message.body()))
                                                .start();
                var client = StompClient.create(fixture.stompTcpUrl(), null, Set.of(new Stomp1_2()))) {
            client.connect();
            client.subscribe(topic);
            try (var transaction = client.beginTransaction()) {
                transaction.send(topic, "deferred", SendOptions.plainText());
                ServerTestSupport.settleFor(Duration.ofMillis(300));
                assertThat(received).isEmpty();
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
        var topic = TestDestinations.uniqueTopic("tx-abort");
        var received = new AtomicInteger();
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .subscription(t -> true)
                                                .handler(message -> received.incrementAndGet())
                                                .start();
                var client = StompClient.create(fixture.stompTcpUrl(), null, Set.of(new Stomp1_2()))) {
            client.connect();
            try (var transaction = client.beginTransaction()) {
                transaction.send(topic, "aborted", SendOptions.plainText());
                transaction.abort();
            }
            ServerTestSupport.settleFor(Duration.ofMillis(500));
            assertThat(received.get()).isZero();
        }
    }
}
