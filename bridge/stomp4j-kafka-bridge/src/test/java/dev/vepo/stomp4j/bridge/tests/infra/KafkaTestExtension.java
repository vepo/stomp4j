package dev.vepo.stomp4j.bridge.tests.infra;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class KafkaTestExtension implements BeforeAllCallback {
    private static final Object LOCK = new Object();
    private static final KafkaTestContainer KAFKA = new KafkaTestContainer();
    private static boolean shutdownHookRegistered;

    static {
        registerShutdownHook();
    }

    public static KafkaTestContainer kafka() {
        return KAFKA;
    }

    private static void registerShutdownHook() {
        if (!shutdownHookRegistered) {
            shutdownHookRegistered = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                synchronized (LOCK) {
                    if (KAFKA.isRunning()) {
                        KAFKA.stop();
                    }
                }
            }, "stomp4j-kafka-shutdown"));
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!KAFKA.isRunning()) {
            KAFKA.start();
        }
    }
}
