package dev.vepo.stomp4j.quarkus.client.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.quarkus.cdi.StompAsync;
import dev.vepo.stomp4j.quarkus.client.StompOutboundCompleted;
import dev.vepo.stomp4j.quarkus.client.StompOutboundMessage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(BrokerTestResource.class)
class StompClientAsyncExtensionTest {

    @Inject
    @StompAsync
    Event<StompOutboundMessage> stompAsync;

    @Inject
    AsyncCompletionTracker tracker;

    @Test
    void shouldFireStompOutboundCompletedForAsyncSend() throws Exception {
        stompAsync.fireAsync(StompOutboundMessage.of("/queue/quarkus-async-test", "async-payload")).toCompletableFuture().get();
        await().atMost(Duration.ofSeconds(15)).until(() -> tracker.completed() != null);
        assertThat(tracker.completed().destination()).isEqualTo("/queue/quarkus-async-test");
        assertThat(tracker.completed().failure()).isNull();
    }
}
