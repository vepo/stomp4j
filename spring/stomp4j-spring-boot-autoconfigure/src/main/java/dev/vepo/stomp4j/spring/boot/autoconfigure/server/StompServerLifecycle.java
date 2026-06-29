package dev.vepo.stomp4j.spring.boot.autoconfigure.server;

import org.springframework.context.SmartLifecycle;

import dev.vepo.stomp4j.server.StompServer;

public class StompServerLifecycle implements SmartLifecycle {

    private final StompServer server;
    private boolean running;

    public StompServerLifecycle(StompServer server) {
        this.server = server;
        this.running = true;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public StompServer server() {
        return server;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        if (running) {
            server.close();
            running = false;
        }
    }
}
