package dev.vepo.stomp4j.quarkus.client.tests.infra;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class StompContainer implements BeforeAllCallback, AfterAllCallback {

    private static StompActiveMqContainer stomp;

    public static StompActiveMqContainer broker() {
        if (stomp == null) {
            stomp = new StompActiveMqContainer();
        }
        return stomp;
    }

    public static void ensureStarted() {
        if (!broker().isRunning()) {
            broker().start();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (stomp != null) {
            stomp.stop();
            stomp = null;
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        ensureStarted();
    }
}
