package dev.vepo.stomp4j.quarkus.client.tests;

import java.util.Map;

import dev.vepo.stomp4j.quarkus.client.tests.infra.StompContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class BrokerTestResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        var container = StompContainer.broker();
        StompContainer.ensureStarted();
        return Map.of(
                      "stomp4j.client.url", container.tcpUrl(),
                      "stomp4j.client.username", container.username(),
                      "stomp4j.client.password", container.password());
    }

    @Override
    public void stop() {
        // Shared broker stays up for the JVM; stopped via shutdown hook in StompContainer.
    }
}
