package dev.vepo.stomp4j.bridge.tests.infra;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class KafkaTestExtension implements BeforeAllCallback, AfterAllCallback {
    private static final KafkaTestContainer KAFKA = new KafkaTestContainer();

    public static KafkaTestContainer kafka() {
        return KAFKA;
    }

    @Override
    public void afterAll(ExtensionContext context) {
        KAFKA.stop();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!KAFKA.isRunning()) {
            KAFKA.start();
        }
    }
}
