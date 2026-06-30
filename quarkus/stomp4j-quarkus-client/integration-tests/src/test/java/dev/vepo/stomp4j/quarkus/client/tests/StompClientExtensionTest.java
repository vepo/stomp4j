package dev.vepo.stomp4j.quarkus.client.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.quarkus.cdi.StompSync;
import dev.vepo.stomp4j.quarkus.client.StompOutboundMessage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@Tag("integration")
@QuarkusTest
@QuarkusTestResource(BrokerTestResource.class)
class StompClientExtensionTest {

    @Inject
    @StompSync
    Event<StompOutboundMessage> stompSync;

    @Inject
    TestConsumer testConsumer;

    @Test
    void shouldDeliverOutboundSyncMessageToInboundObserver() {
        stompSync.fire(StompOutboundMessage.of("/queue/quarkus-test", "hello-quarkus"));
        await().atMost(Duration.ofSeconds(15)).until(() -> "hello-quarkus".equals(testConsumer.lastMessage()));
        assertThat(testConsumer.lastMessage()).isEqualTo("hello-quarkus");
    }
}
