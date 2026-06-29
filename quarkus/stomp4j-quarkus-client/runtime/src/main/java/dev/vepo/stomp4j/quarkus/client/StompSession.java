package dev.vepo.stomp4j.quarkus.client;

import dev.vepo.stomp4j.client.StompClient;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

@ApplicationScoped
public class StompSession {

    private final StompClientFactory factory;
    private StompClient client;

    @Inject
    public StompSession(StompClientFactory factory) {
        this.factory = factory;
    }

    public StompClient client() {
        if (client == null) {
            throw new IllegalStateException("Stomp client is not connected");
        }
        return client;
    }

    void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION - 100) StartupEvent event) {
        if (client == null) {
            client = factory.create();
            client.connect();
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
