package dev.vepo.stomp4j.quarkus.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.StompDelivery;
import dev.vepo.stomp4j.integration.client.Acknowledgment;
import dev.vepo.stomp4j.integration.client.InboundAckPolicy;
import dev.vepo.stomp4j.quarkus.cdi.StompAsync;
import dev.vepo.stomp4j.quarkus.cdi.StompSync;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

@ApplicationScoped
public class StompInboundRegistrar {

    private final StompSession session;
    private final StompInboundEndpointRegistry registry;
    private final Event<StompInboundMessage> inboundEvents;
    private ExecutorService executor;

    @Inject
    public StompInboundRegistrar(StompSession session,
                                 StompInboundEndpointRegistry registry,
                                 Event<StompInboundMessage> inboundEvents) {
        this.session = session;
        this.registry = registry;
        this.inboundEvents = inboundEvents;
    }

    private void applyAckRules(AckMode ackMode, StompDelivery delivery, boolean threw) {
        InboundAckPolicy.afterInvocation(ackMode, delivery, Acknowledgment.of(delivery), threw);
    }

    private void dispatch(StompInboundEndpoint endpoint, StompDelivery delivery) {
        var message = new StompInboundMessage(endpoint.destination(), delivery);
        if (endpoint.dispatchMode() == StompDispatchMode.ASYNC) {
            executor.execute(() -> fireInbound(endpoint, message, delivery));
        } else {
            fireInbound(endpoint, message, delivery);
        }
    }

    private void fireInbound(StompInboundEndpoint endpoint,
                             StompInboundMessage message,
                             StompDelivery delivery) {
        var selected = inboundEvents
                                    .select(new StompDestination.Literal(endpoint.destination()))
                                    .select(endpoint.dispatchMode() == StompDispatchMode.ASYNC
                                                                                               ? StompAsync.Literal.INSTANCE
                                                                                               : StompSync.Literal.INSTANCE);
        var threw = false;
        try {
            selected.fire(message);
        } catch (RuntimeException ex) {
            threw = true;
            throw ex;
        } finally {
            applyAckRules(endpoint.ackMode(), delivery, threw);
        }
    }

    void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION) StartupEvent event) {
        if (registry.endpoints().isEmpty()) {
            registry.setEndpoints(StompInboundClasspathScanner.scanApplicationBeans());
        }
        var endpoints = registry.endpoints();
        if (endpoints.isEmpty()) {
            return;
        }
        executor = Executors.newCachedThreadPool(r -> {
            var thread = new Thread(r, "stomp-inbound-");
            thread.setDaemon(true);
            return thread;
        });
        var client = session.client();
        for (var endpoint : endpoints) {
            client.subscribe(endpoint.destination(), endpoint.ackMode(), delivery -> dispatch(endpoint, delivery));
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
