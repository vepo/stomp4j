package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.LinkedList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.server.tests.infra.EmbeddedServerFixture;
import dev.vepo.stomp4j.server.tests.infra.TestDestinations;
import dev.vepo.stomp4j.server.tests.infra.TestSsl;

@Execution(ExecutionMode.CONCURRENT)
class StompServerTlsTest {
    private record ReceivedMessage(String topic, String message) {}

    @Test
    @DisplayName("Secure TCP client should exchange messages with SSL-enabled server")
    @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void secureTcpRoundTripTest() {
        var topic = TestDestinations.uniqueTopic("secure");
        var received = new LinkedList<ReceivedMessage>();
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .subscription(topic -> true)
                                                .handler(message -> received.offer(
                                                                                   new ReceivedMessage(message.destination(), message.body())))
                                                .ssl(TestSsl.serverSslContext())
                                                .start();
                var client = StompClient.create(fixture.stompSecureTcpUrl(), TestSsl.trustingClientSslContext())) {
            client.connect();
            client.sendPlain(topic, "secure-body", "text/plain");
            await().atMost(Duration.ofSeconds(10)).until(() -> !received.isEmpty());
            assertThat(received).containsExactly(new ReceivedMessage(topic, "secure-body"));
        }
    }

    @Test
    @DisplayName("Secure WebSocket client should exchange messages with SSL-enabled server")
    @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void secureWebSocketRoundTripTest() {
        var topic = TestDestinations.uniqueTopic("secure-ws");
        var received = new LinkedList<ReceivedMessage>();
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withWebSocket()
                                                .subscription(topic -> true)
                                                .handler(message -> received.offer(
                                                                                   new ReceivedMessage(message.destination(), message.body())))
                                                .ssl(TestSsl.serverSslContext(),
                                                     TestSsl.keyStorePath(),
                                                     TestSsl.keyStorePassword())
                                                .start();
                var client = StompClient.create(fixture.secureWebSocketUrl(), TestSsl.trustingClientSslContext())) {
            client.connect();
            client.sendPlain(topic, "secure-ws-body", "text/plain");
            await().atMost(Duration.ofSeconds(10)).until(() -> !received.isEmpty());
            assertThat(received).containsExactly(new ReceivedMessage(topic, "secure-ws-body"));
        }
    }
}
