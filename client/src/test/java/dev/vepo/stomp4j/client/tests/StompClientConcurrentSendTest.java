package dev.vepo.stomp4j.client.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.client.tests.infra.ArtemisJmsFixture;
import dev.vepo.stomp4j.client.tests.infra.StompActiveMqContainer;
import dev.vepo.stomp4j.client.tests.infra.StompContainer;
import dev.vepo.stomp4j.client.tests.infra.StompTestSupport;

@Tag("integration")
@ExtendWith(StompContainer.class)
@Execution(ExecutionMode.SAME_THREAD)
class StompClientConcurrentSendTest {

    private static final int THREAD_COUNT = 8;
    private static final int SENDS_PER_THREAD = 25;

    @Test
    @DisplayName("StompClient delivers every frame when multiple threads send concurrently")
    void shouldDeliverAllFramesWhenSendingConcurrentlyFromMultipleThreads(StompActiveMqContainer broker) throws Exception {
        try (var jms = ArtemisJmsFixture.openTopic(broker);
                StompClient client = StompClient.create(broker.tcpUrl(),
                                                        new UserCredential(broker.username(), broker.password()))) {
            var topic = jms.destinationName();
            var received = StompTestSupport.threadSafeMessageList();
            client.connect();
            client.subscribe(topic, received::add);
            StompTestSupport.settleSubscription();

            var sendPool = Executors.newFixedThreadPool(THREAD_COUNT);
            try {
                var tasks = sendPool.invokeAll(
                                               java.util.stream.IntStream.range(0, THREAD_COUNT)
                                                                         .mapToObj(threadId -> (java.util.concurrent.Callable<Void>) () -> {
                                                                             for (var sendIndex = 0; sendIndex < SENDS_PER_THREAD; sendIndex++) {
                                                                                 client.sendPlain(topic,
                                                                                                  "thread-%d-send-%d".formatted(threadId, sendIndex),
                                                                                                  "text/plain");
                                                                             }
                                                                             return null;
                                                                         })
                                                                         .toList());
                for (var task : tasks) {
                    task.get(60, TimeUnit.SECONDS);
                }
            } finally {
                sendPool.shutdownNow();
            }

            StompTestSupport.awaitMessageCount(THREAD_COUNT * SENDS_PER_THREAD, received::size);
            assertThat(new HashSet<>(received)).hasSize(THREAD_COUNT * SENDS_PER_THREAD);
        }
    }
}
