package dev.vepo.stomp4j.quarkus.client.tests;

import java.util.concurrent.atomic.AtomicReference;

import dev.vepo.stomp4j.quarkus.client.StompOutboundCompleted;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class AsyncCompletionTracker {

    private final AtomicReference<StompOutboundCompleted> completed = new AtomicReference<>();

    StompOutboundCompleted completed() {
        return completed.get();
    }

    void onCompleted(@Observes StompOutboundCompleted event) {
        completed.set(event);
    }
}
