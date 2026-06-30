package dev.vepo.stomp4j.bridge.tests.infra;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public final class KafkaTestContainer extends GenericContainer<KafkaTestContainer> {
    private static final int KAFKA_PORT = 9092;
    private static final DockerImageName IMAGE = DockerImageName.parse("apache/kafka-native:3.8.0");

    public KafkaTestContainer() {
        super(IMAGE);
        withReuse(true);
        withEnv("KAFKA_NODE_ID", "1");
        withEnv("KAFKA_PROCESS_ROLES", "broker,controller");
        withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:%d,CONTROLLER://0.0.0.0:9093".formatted(KAFKA_PORT));
        withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:%d".formatted(KAFKA_PORT));
        withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER");
        withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT");
        withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093");
        withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1");
        withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
        withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
        withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0");
        addFixedExposedPort(KAFKA_PORT, KAFKA_PORT);
        waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
    }

    public String bootstrapServers() {
        return "localhost:%d".formatted(KAFKA_PORT);
    }
}
