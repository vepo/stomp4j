package dev.vepo.stomp4j.spring.boot.autoconfigure.client;

import org.springframework.context.SmartLifecycle;

import dev.vepo.stomp4j.client.StompClient;

public class StompClientConnectionManager implements SmartLifecycle {

    private final StompClientFactory factory;
    private StompClient client;
    private boolean running;

    public StompClientConnectionManager(StompClientFactory factory) {
        this.factory = factory;
    }

    public StompClient client() {
        if (!running || client == null) {
            throw new IllegalStateException("Stomp client is not connected");
        }
        return client;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void start() {
        if (!running) {
            client = factory.create();
            client.connect();
            running = true;
        }
    }

    @Override
    public void stop() {
        if (running && client != null) {
            client.close();
            client = null;
            running = false;
        }
    }
}
