package dev.vepo.stomp4j.bridge.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.bridge.StompKafkaBridge;

public final class StompKafkaBridgeApplication {

    private static final Logger logger = LoggerFactory.getLogger(StompKafkaBridgeApplication.class);

    public static void main(String[] args) throws InterruptedException {
        try (var bridge = BridgeConfigLoader.load()) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down STOMP-Kafka bridge");
                bridge.close();
            }));
            logger.info("STOMP-Kafka bridge started");
            bridge.awaitShutdown();
        }
    }

    private StompKafkaBridgeApplication() {}
}
