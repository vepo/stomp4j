package io.vepo.stomp4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.vepo.stomp4j.infra.StompActiveMqContainer;
import io.vepo.stomp4j.protocol.Stomp;
import io.vepo.stomp4j.protocol.v1_0.Stomp1_0;
import io.vepo.stomp4j.protocol.v1_1.Stomp1_1;
import io.vepo.stomp4j.protocol.v1_2.Stomp1_2;
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

    private static Stream<Arguments> allVersions() {
        return Stream.of(Arguments.of(new Stomp1_0()),
                         Arguments.of(new Stomp1_1()),
                         Arguments.of(new Stomp1_2()));
    }

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

    @ParameterizedTest
    @MethodSource("allVersions")
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void versionTest(Stomp version) throws InterruptedException, JMSException {
        try (StompClient client = new StompClient(stomp.webSocketUrl(),
                                                  new UserCredential(stomp.username(), stomp.password()),
                                                  TransportType.WEB_SOCKET,
                                                  Set.of(version))) {
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
            client.unsubscribe(topicName);
            sendMessage("message-11");
            await().pollDelay(Durations.ONE_SECOND).until(() -> true);
            assertThat(messageList).size().isEqualTo(10);
            sendMessage("message-12");
            assertThat(messageList).size().isEqualTo(10);
            sendMessage("message-13");
            assertThat(messageList).size().isEqualTo(10);
        }
    }
}
