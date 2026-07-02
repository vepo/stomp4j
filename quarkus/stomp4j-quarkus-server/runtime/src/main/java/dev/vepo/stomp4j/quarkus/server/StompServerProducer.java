package dev.vepo.stomp4j.quarkus.server;

import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.integration.server.CompositeStompDestinationHandler;
import dev.vepo.stomp4j.server.StompConnectionListener;
import dev.vepo.stomp4j.server.StompServer;
import dev.vepo.stomp4j.server.SubscriptionHandler;
import dev.vepo.stomp4j.server.auth.StompAuthenticator;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@ApplicationScoped
public class StompServerProducer {

    private final StompServerConfig config;
    private final Instance<StompInboundHandler> inboundHandlers;
    private final Instance<StompAuthenticator> authenticator;
    private final Instance<SubscriptionHandler> subscriptionHandler;
    private final Instance<StompConnectionListener> connectionListener;
    private StompServer server;

    @Inject
    public StompServerProducer(StompServerConfig config,
                               Instance<StompInboundHandler> inboundHandlers,
                               Instance<StompAuthenticator> authenticator,
                               Instance<SubscriptionHandler> subscriptionHandler,
                               Instance<StompConnectionListener> connectionListener) {
        this.config = config;
        this.inboundHandlers = inboundHandlers;
        this.authenticator = authenticator;
        this.subscriptionHandler = subscriptionHandler;
        this.connectionListener = connectionListener;
    }

    private StompServer buildServer() {
        var builder = StompServer.builder()
                                 .serverName(config.serverName())
                                 .heartbeat(config.heartbeat());
        if (config.channels().isEmpty()) {
            builder.channel(TransportType.TCP, 5500);
        } else {
            config.channels().forEach(channel -> builder.channel(channel.type(), channel.port()));
        }
        var handlers = inboundHandlers.stream().toList();
        if (handlers.isEmpty()) {
            builder.handler(message -> {});
        } else {
            builder.handler(new CompositeStompDestinationHandler(handlers));
        }
        if (subscriptionHandler.isResolvable()) {
            builder.subscription(subscriptionHandler.get());
        } else {
            builder.subscription(topic -> true);
        }
        if (authenticator.isResolvable()) {
            builder.authenticator(authenticator.get());
        }
        if (connectionListener.isResolvable()) {
            builder.connectionListener(connectionListener.get());
        }
        return builder.start();
    }

    private void ensureStarted() {
        if (server == null) {
            server = buildServer();
        }
    }

    void onStart(@Observes StartupEvent event) {
        if (config.enabled()) {
            ensureStarted();
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Produces
    @Singleton
    public StompServer stompServer() {
        ensureStarted();
        return server;
    }
}
