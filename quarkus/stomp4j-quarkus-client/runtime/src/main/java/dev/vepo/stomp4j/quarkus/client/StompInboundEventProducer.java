package dev.vepo.stomp4j.quarkus.client;

import dev.vepo.stomp4j.quarkus.cdi.StompAsync;
import dev.vepo.stomp4j.quarkus.cdi.StompSync;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class StompInboundEventProducer {

    @Produces
    @StompAsync
    Event<StompInboundMessage> stompInboundAsync(Event<StompInboundMessage> events) {
        return events.select(StompAsync.Literal.INSTANCE);
    }

    @Produces
    @StompSync
    Event<StompInboundMessage> stompInboundSync(Event<StompInboundMessage> events) {
        return events.select(StompSync.Literal.INSTANCE);
    }
}
