package dev.vepo.stomp4j.quarkus.client.tests.infra;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class StompContainer implements BeforeAllCallback {
    private static final Object LOCK = new Object();
    private static StompActiveMqContainer stomp;
    private static boolean shutdownHookRegistered;

    public static StompActiveMqContainer broker() {
        synchronized (LOCK) {
            if (stomp == null) {
                stomp = new StompActiveMqContainer();
                registerShutdownHook();
            }
            return stomp;
        }
    }

    public static void ensureStarted() {
        if (!broker().isRunning()) {
            broker().start();
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        ensureStarted();
    }

    private static void registerShutdownHook() {
        if (!shutdownHookRegistered) {
            shutdownHookRegistered = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                synchronized (LOCK) {
                    if (stomp != null && stomp.isRunning()) {
                        stomp.stop();
                    }
                }
            }, "stomp4j-quarkus-broker-shutdown"));
        }
    }
}
