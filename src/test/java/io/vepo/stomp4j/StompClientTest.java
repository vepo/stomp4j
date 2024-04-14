package io.vepo.stomp4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.activemq.ActiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class StompClientTest {

    @Container
    private static ActiveMQContainer activemq = new ActiveMQContainer("apache/activemq-classic:6.1.0");

    @Test
    @Timeout(30)
    void simpleTest() {
        try (StompClient client = new StompClient("ws://" + activemq.getHost() + ":" + activemq.getMappedPort(61613))) {
            client.connect();
        }
    }
}
