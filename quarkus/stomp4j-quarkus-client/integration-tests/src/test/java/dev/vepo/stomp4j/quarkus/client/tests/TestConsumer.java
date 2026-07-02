package dev.vepo.stomp4j.quarkus.client.tests;

import java.util.concurrent.atomic.AtomicReference;

import dev.vepo.stomp4j.quarkus.cdi.StompAsync;
import dev.vepo.stomp4j.integration.client.Acknowledgment;
import dev.vepo.stomp4j.quarkus.client.StompDestination;
import dev.vepo.stomp4j.quarkus.client.StompInboundMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class TestConsumer {

    private final AtomicReference<String> lastMessage = new AtomicReference<>();

    String lastMessage() {
        return lastMessage.get();
    }

    public void onMessage(@Observes @StompAsync @StompDestination("/queue/quarkus-test") StompInboundMessage message) {
        lastMessage.set(message.delivery().body());
        Acknowledgment.of(message.delivery()).acknowledge();
    }
}
