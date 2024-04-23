package io.vepo.stomp4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.ArrayList;
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

import io.vepo.stomp4j.infra.StompActiveMqContainer;
import io.vepo.stomp4j.protocol.v1_0.Stomp1_0;
import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jakarta.jms.Topic;

@Testcontainers
public class StompClientTcpTest {

    private static final Logger logger = LoggerFactory.getLogger(StompClientTcpTest.class);

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

    void sendMessage(String content) throws JMSException {
        try (var producer = session.createProducer(topic)) {
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(session.createTextMessage(content));
        }
    }

    @AfterEach
    void tearDown() throws JMSException {
        session.close();
        connection.close();
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void subscribeTest() throws InterruptedException, JMSException {
        try (StompClient client = new StompClient(stomp.tcpUrl(),
                                                  new UserCredential(stomp.username(), stomp.password()),
                                                  Set.of(new Stomp1_0()))) {
            var messageList = new ArrayList<String>();
            client.connect();
            client.subscribe(topicName, message -> {
                messageList.add(message);
            });
            sendMessage("message-01");
            sendMessage("message-02");
            sendMessage("message-03");
            sendMessage("message-04");
            sendMessage("message-05");
            sendMessage("message-06");
            sendMessage("message-07");
            sendMessage("message-08");
            sendMessage("message-09");
            sendMessage("message-10");
            await().until(() -> messageList.size() == 10);
            assertThat(messageList).containsExactly("message-01", "message-02", "message-03", "message-04",
                                                    "message-05", "message-06", "message-07", "message-08",
                                                    "message-09", "message-10");
        }
    }
}
