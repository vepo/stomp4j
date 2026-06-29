package dev.vepo.stomp4j.quarkus.client;

import dev.vepo.stomp4j.quarkus.cdi.StompAsync;
import dev.vepo.stomp4j.quarkus.cdi.StompSync;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class StompOutboundEventProducer {

    @Produces
    @StompAsync
    Event<StompOutboundMessage> stompOutboundAsync(Event<StompOutboundMessage> events) {
        return events.select(StompAsync.Literal.INSTANCE);
    }

    @Produces
    @StompSync
    Event<StompOutboundMessage> stompOutboundSync(Event<StompOutboundMessage> events) {
        return events.select(StompSync.Literal.INSTANCE);
    }
}
