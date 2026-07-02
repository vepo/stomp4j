package dev.vepo.stomp4j.spring.boot.autoconfigure.client;

import org.springframework.context.SmartLifecycle;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.exceptions.StompException;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Knowing:</b> Whether the Spring-managed client lifecycle is running
 * and holds a connected {@link StompClient}.</li>
 * <li><b>Doing:</b> On context {@link #start()}, create a client via
 * {@link StompClientFactory}, {@link StompClient#connect()}, and on
 * {@link #stop()} close and release it; expose the connected client to
 * collaborators.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> {@link StompClientFactory}, {@link StompClient},
 * {@link StompClientTemplate}, {@link StompListenerEndpointRegistrar}
 * </p>
 * <p>
 * <b>Not responsible for:</b> broker URL or credential configuration
 * ({@link StompClientProperties}, {@link StompClientFactory}), STOMP protocol
 * handling, send/subscribe API ({@link StompClientTemplate}), or
 * {@link StompListener @StompListener} registration.
 * </p>
 */
public class StompClientLifecycle implements SmartLifecycle {

    private final StompClientFactory factory;
    private StompClient client;
    private boolean running;

    public StompClientLifecycle(StompClientFactory factory) {
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
            var created = factory.create();
            try {
                created.connect();
            } catch (StompException ex) {
                created.close();
                throw ex;
            }
            client = created;
            running = true;
        }
    }

    @Override
    public void stop() {
        if (client != null) {
            client.close();
            client = null;
        }
        running = false;
    }
}
