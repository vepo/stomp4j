package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

@Execution(ExecutionMode.SAME_THREAD)
class TcpChannelConcurrentOutboundTest {

    private static final int THREAD_COUNT = 8;
    private static final int SENDS_PER_THREAD = 25;

    @Test
    @DisplayName("TcpChannel delivers every outbound frame when handler threads broadcast concurrently")
    @Timeout(30)
    void shouldDeliverAllFramesWhenBroadcastingConcurrently() throws Exception {
        var topic = TestDestinations.uniqueTopic("concurrent-outbound");
        var received = Collections.synchronizedList(new ArrayList<String>());

        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .subscription(destination -> true)
                                                .handler(message -> {})
                                                .start();
                var client = StompClient.create(fixture.stompTcpUrl())) {
            client.connect();
            client.subscribe(topic, received::add);

            var sendPool = Executors.newFixedThreadPool(THREAD_COUNT);
            try {
                var tasks = sendPool.invokeAll(
                                               java.util.stream.IntStream.range(0, THREAD_COUNT)
                                                                         .mapToObj(threadId -> (java.util.concurrent.Callable<Void>) () -> {
                                                                             for (var sendIndex = 0; sendIndex < SENDS_PER_THREAD; sendIndex++) {
                                                                                 var body = "thread-%d-send-%d".formatted(threadId, sendIndex);
                                                                                 fixture.outboundChannel()
                                                                                        .send(new Message(Command.SEND,
                                                                                                          Headers.builder()
                                                                                                                 .with(Header.DESTINATION, topic)
                                                                                                                 .build(),
                                                                                                          body));
                                                                             }
                                                                             return null;
                                                                         })
                                                                         .toList());
                for (var task : tasks) {
                    task.get(20, TimeUnit.SECONDS);
                }
            } finally {
                sendPool.shutdownNow();
            }

            await().atMost(Duration.ofSeconds(15))
                   .until(() -> received.size() == THREAD_COUNT * SENDS_PER_THREAD);
            assertThat(new HashSet<>(received)).hasSize(THREAD_COUNT * SENDS_PER_THREAD);
        }
    }
}
