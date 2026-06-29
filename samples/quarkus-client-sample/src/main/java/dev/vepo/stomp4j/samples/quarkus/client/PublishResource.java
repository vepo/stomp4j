package dev.vepo.stomp4j.samples.quarkus.client;

import java.time.Duration;

import dev.vepo.stomp4j.quarkus.cdi.StompSync;
import dev.vepo.stomp4j.quarkus.client.StompOutboundMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/publish")
@ApplicationScoped
public class PublishResource {

    private final Event<StompOutboundMessage> stompSync;

    @Inject
    public PublishResource(@StompSync Event<StompOutboundMessage> stompSync) {
        this.stompSync = stompSync;
    }

    @POST
    public String publish(String body) {
        stompSync.fire(StompOutboundMessage.of("/queue/demo.out", body)
                                           .withReceipt(Duration.ofSeconds(30)));
        return "sent";
    }
}
