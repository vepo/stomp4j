package dev.vepo.stomp4j.quarkus.client.tests;

import java.util.Map;

import dev.vepo.stomp4j.quarkus.client.tests.infra.StompActiveMqContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class BrokerTestResource implements QuarkusTestResourceLifecycleManager {

    private StompActiveMqContainer container;

    @Override
    public Map<String, String> start() {
        container = new StompActiveMqContainer();
        container.start();
        return Map.of(
                      "stomp4j.client.url", container.tcpUrl(),
                      "stomp4j.client.username", container.username(),
                      "stomp4j.client.password", container.password());
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
