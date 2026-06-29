package dev.vepo.stomp4j.quarkus.server;

import dev.vepo.stomp4j.quarkus.cdi.StompAsync;
import dev.vepo.stomp4j.quarkus.cdi.StompSync;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class StompServerEventProducer {

    @Produces
    @StompAsync
    Event<StompServerMessage> stompServerAsync(Event<StompServerMessage> events) {
        return events.select(StompAsync.Literal.INSTANCE);
    }

    @Produces
    @StompSync
    Event<StompServerMessage> stompServerSync(Event<StompServerMessage> events) {
        return events.select(StompSync.Literal.INSTANCE);
    }
}
