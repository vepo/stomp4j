module stomp4j.kafka.bridge.test {
    requires stomp4j.kafka.bridge;
    requires stomp4j.client;
    requires org.junit.jupiter.api;
    requires org.assertj.core;
    requires awaitility;
    requires kafka.clients;
    requires testcontainers;

    opens dev.vepo.stomp4j.bridge.tests to org.junit.platform.commons;
    opens dev.vepo.stomp4j.bridge.tests.infra to org.junit.platform.commons;
}
