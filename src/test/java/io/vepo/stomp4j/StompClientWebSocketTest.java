package io.vepo.stomp4j;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.vepo.stomp4j.StompClient.TransportType;
import io.vepo.stomp4j.infra.StompActiveMqContainer;
import io.vepo.stomp4j.protocol.v1_0.Stomp1_0;
import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jakarta.jms.Topic;

@Testcontainers
public class StompClientWebSocketTest {

    private static final Logger logger = LoggerFactory.getLogger(StompClientWebSocketTest.class);

    @Container
    private static StompActiveMqContainer stomp = new StompActiveMqContainer();

    private String topicName;
    private Connection connection;
    private Session session;
    private Topic topic;

    @BeforeEach
    void setup() throws JMSException {
        var connectionFactory = new ActiveMQConnectionFactory(stomp.clientUrl());
        connection = connectionFactory.createConnection(stomp.username(), stomp.password());
        // topic
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        topicName = "topic-" + UUID.randomUUID().toString();
        topic = session.createTopic(topicName);
        logger.info("Created topic: {}", topic);
    }

    void sendMessage() throws JMSException {
        var producer = session.createProducer(topic);
        producer.setDeliveryMode(DeliveryMode.PERSISTENT);
        producer.send(session.createTextMessage("Hello World"));
        producer.close();
    }

    @AfterEach
    void tearDown() throws JMSException {
        session.close();
        connection.close();
    }

    @Test
    @Timeout(30)
    void simpleTest() throws InterruptedException, JMSException {
        try (StompClient client = new StompClient(stomp.webSocketUrl(),
                                                  new UserCredential(stomp.username(), stomp.password()),
                                                  TransportType.WEB_SOCKET,
                                                  Set.of(new Stomp1_0()))) {
            var countDown = new CountDownLatch(1);
            client.connect();
            client.subscribe(topicName, message -> {
                System.out.println("Message received: " + message);
                countDown.countDown();
            });
            sendMessage();
            countDown.await();
        }
    }
}
