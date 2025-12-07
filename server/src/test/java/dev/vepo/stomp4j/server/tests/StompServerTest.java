package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.LinkedList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Headers;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.StompServer;

class StompServerTest {
    private record ReceivedMessage(String topic, String message) {}

    @Test
    @DisplayName("A server should be able to read a message from clients")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void receiveMessageTest() {
        var receiveMessages = new LinkedList<ReceivedMessage>();
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5500)
                                     .channel(TransportType.WEB_SOCKET, 5501)
                                     .authenticator(credentials -> true)
                                     .handler((topic, message, writer) -> receiveMessages.offer(new ReceivedMessage(topic, message)))
                                     .start();
                var tcpClient = StompClient.create("stomp://localhost:5500")) {
            tcpClient.connect();
            var subscription = tcpClient.subscribe("topic-1");
            assertThat(subscription.hasData()).isFalse();
            tcpClient.sendPlain("topic-1", "MESSAGE-1", "text/plain");
            await().until(() -> !receiveMessages.isEmpty());
            assertThat(receiveMessages).hasSize(1)
                                       .containsExactly(new ReceivedMessage("topic-1", "MESSAGE-1"));
        }
    }

    @Test
    @DisplayName("A server should be able to read a message from clients")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void sendMessageTest() {
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5510)
                                     .channel(TransportType.WEB_SOCKET, 5511)
                                     .authenticator(credentials -> true)
                                     .start();
                var tcpClient = StompClient.create("stomp://localhost:5510")) {
            tcpClient.connect();
            var subscription = tcpClient.subscribe("topic-1");
            assertThat(subscription.hasData()).isFalse();
            server.outboundChannel()
                  .send(new Message(Command.SEND, Headers.builder().with(Header.DESTINATION, "topic-1").build(), "MESSAGE-1"));
            await().until(() -> subscription.hasData());
            var receiveMessages = subscription.poll();
            assertThat(receiveMessages).hasSize(1)
                                       .containsExactly("MESSAGE-1");
        }
    }
}
