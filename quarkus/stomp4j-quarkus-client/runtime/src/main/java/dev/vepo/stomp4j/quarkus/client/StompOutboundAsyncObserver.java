package dev.vepo.stomp4j.quarkus.client;

import dev.vepo.stomp4j.quarkus.cdi.StompAsync;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class StompOutboundAsyncObserver {

    private final StompSession session;
    private final Event<StompOutboundCompleted> completed;

    @Inject
    public StompOutboundAsyncObserver(StompSession session, Event<StompOutboundCompleted> completed) {
        this.session = session;
        this.completed = completed;
    }

    void onSend(@ObservesAsync @StompAsync StompOutboundMessage message) {
        try {
            var client = session.client();
            if (message.options().receipt()) {
                var receipt = client.send(message.destination(), message.body(), message.options());
                receipt.completion().whenComplete((ignored, error) -> completed.fire(new StompOutboundCompleted(
                                                                                                                message.destination(),
                                                                                                                receipt.receiptId(),
                                                                                                                error)));
            } else {
                client.send(message.destination(), message.body(), message.options());
                completed.fire(new StompOutboundCompleted(message.destination(), null, null));
            }
        } catch (Exception ex) {
            completed.fire(new StompOutboundCompleted(message.destination(), null, ex));
        }
    }
}
