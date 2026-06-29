package dev.vepo.stomp4j.quarkus.server;

import dev.vepo.stomp4j.quarkus.cdi.StompAsync;
import dev.vepo.stomp4j.quarkus.cdi.StompSync;
import dev.vepo.stomp4j.server.StompServer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class StompServerOutboundObserver {

    private final StompServer server;

    @Inject
    public StompServerOutboundObserver(StompServer server) {
        this.server = server;
    }

    void onSendAsync(@ObservesAsync @StompAsync StompServerMessage message) {
        server.outboundChannel().send(message.frame());
    }

    void onSendSync(@Observes @StompSync StompServerMessage message) {
        server.outboundChannel().send(message.frame());
    }
}
