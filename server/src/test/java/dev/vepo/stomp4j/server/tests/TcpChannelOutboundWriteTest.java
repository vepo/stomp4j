package dev.vepo.stomp4j.server.tests;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Headers;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.tests.infra.EmbeddedServerFixture;
import dev.vepo.stomp4j.server.tests.infra.TestDestinations;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.SAME_THREAD)
class TcpChannelOutboundWriteTest {

    @Test
    @DisplayName("TcpChannel delivers a large outbound frame over plain TCP without truncation")
    @Timeout(value = 15)
    void shouldDeliverLargeOutboundFrameOverPlainTcp() {
        var topic = TestDestinations.uniqueTopic("large-outbound");
        var subscribed = new AtomicBoolean(false);
        var body = "payload-".repeat(1024);

        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .subscription(destination -> subscribed.compareAndSet(false, true))
                                                .handler(message -> {})
                                                .start();
                var client = StompClient.create(fixture.stompTcpUrl())) {
            client.connect();
            var subscription = client.subscribe(topic);
            await().atMost(Duration.ofSeconds(5)).until(subscribed::get);

            fixture.outboundChannel()
                   .send(new Message(Command.SEND,
                                     Headers.builder().with(Header.DESTINATION, topic).build(),
                                     body));

            await().atMost(Duration.ofSeconds(5)).until(subscription::hasData);
            assertThat(subscription.poll()).containsExactly(body);
        }
    }
}
