package dev.vepo.stomp4j.quarkus.client;

import dev.vepo.stomp4j.quarkus.cdi.StompSync;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class StompOutboundSyncObserver {

    private final StompSession session;

    @Inject
    public StompOutboundSyncObserver(StompSession session) {
        this.session = session;
    }

    void onSend(@Observes @StompSync StompOutboundMessage message) {
        var client = session.client();
        if (message.options().receipt()) {
            client.send(message.destination(), message.body(), message.options())
                  .completion()
                  .join();
        } else {
            client.send(message.destination(), message.body(), message.options());
        }
    }
}
