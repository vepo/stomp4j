package dev.vepo.stomp4j.samples.quarkus.client;

import java.util.concurrent.atomic.AtomicReference;

import dev.vepo.stomp4j.quarkus.cdi.StompAsync;
import dev.vepo.stomp4j.quarkus.client.Acknowledgment;
import dev.vepo.stomp4j.quarkus.client.StompDestination;
import dev.vepo.stomp4j.quarkus.client.StompInboundMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class DemoInboundConsumer {

    private final AtomicReference<String> lastInbound = new AtomicReference<>();

    public String lastInbound() {
        return lastInbound.get();
    }

    void onInbound(@Observes @StompAsync @StompDestination("/queue/demo.in") StompInboundMessage message) {
        lastInbound.set(message.delivery().body());
        Acknowledgment.of(message.delivery()).acknowledge();
    }
}
