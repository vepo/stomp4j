package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.LinkedList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.server.StompServer;
import dev.vepo.stomp4j.server.tests.infra.TestSsl;

class StompServerTlsTest {
    private record ReceivedMessage(String topic, String message) {}

    @Test
    @DisplayName("Secure TCP client should exchange messages with SSL-enabled server")
    @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void secureTcpRoundTripTest() {
        var received = new LinkedList<ReceivedMessage>();
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5510)
                                     .subscription(topic -> true)
                                     .handler(message -> received.offer(
                                             new ReceivedMessage(message.destination(), message.body())))
                                     .ssl(TestSsl.serverSslContext())
                                     .start();
                var client = StompClient.create("stomps://localhost:5510", TestSsl.trustingClientSslContext())) {
            client.connect();
            client.sendPlain("secure-topic", "secure-body", "text/plain");
            await().until(() -> !received.isEmpty());
            assertThat(received).containsExactly(new ReceivedMessage("secure-topic", "secure-body"));
        }
    }

    @Test
    @DisplayName("Secure WebSocket client should exchange messages with SSL-enabled server")
    @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void secureWebSocketRoundTripTest() {
        var received = new LinkedList<ReceivedMessage>();
        try (var server = StompServer.builder()
                                     .channel(TransportType.WEB_SOCKET, 5511)
                                     .subscription(topic -> true)
                                     .handler(message -> received.offer(
                                             new ReceivedMessage(message.destination(), message.body())))
                                     .ssl(TestSsl.serverSslContext(),
                                          TestSsl.keyStorePath(),
                                          TestSsl.keyStorePassword())
                                     .start();
                var client = StompClient.create("wss://localhost:5511", TestSsl.trustingClientSslContext())) {
            client.connect();
            client.sendPlain("secure-ws-topic", "secure-ws-body", "text/plain");
            await().until(() -> !received.isEmpty());
            assertThat(received).containsExactly(new ReceivedMessage("secure-ws-topic", "secure-ws-body"));
        }
    }
}
