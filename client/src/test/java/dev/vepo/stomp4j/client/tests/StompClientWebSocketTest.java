package dev.vepo.stomp4j.client.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.client.protocol.Stomp;
import dev.vepo.stomp4j.client.protocol.v1_0.Stomp1_0;
import dev.vepo.stomp4j.client.protocol.v1_1.Stomp1_1;
import dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;
import dev.vepo.stomp4j.client.tests.infra.StompActiveMqContainer;
import dev.vepo.stomp4j.client.tests.infra.StompTestSupport;
import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jakarta.jms.Topic;

@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)
class StompClientWebSocketTest {

    private static final Logger logger = LoggerFactory.getLogger(StompClientWebSocketTest.class);

    private static StompActiveMqContainer stomp;

    private static Stream<Arguments> allVersions() {
        return Stream.of(Arguments.of(new Stomp1_0()),
                         Arguments.of(new Stomp1_1()),
                         Arguments.of(new Stomp1_2()));
    }

    private String topicName;
    private Connection connection;
    private Session session;

    private Topic topic;

    @BeforeAll
    static void startBroker() {
        stomp = new StompActiveMqContainer();
        stomp.start();
    }

    @AfterAll
    static void stopBroker() {
        if (Objects.nonNull(stomp)) {
            stomp.stop();
        }
    }

    Optional<String> receiveMessage(String content, Duration timeout) {
        try (var consumer = session.createConsumer(topic)) {
            long startTime = System.nanoTime();
            while (System.nanoTime() - startTime < timeout.toNanos()) {
                logger.info("Trying to read message...");
                var message = consumer.receive(timeout.toMillis());
                logger.info("Message received: {}", message);
                if (Objects.nonNull(message) && message.isBodyAssignableTo(byte[].class)) {
                    return Optional.ofNullable(new String(message.getBody(byte[].class)));
                }
            }
            return Optional.empty();
        } catch (JMSException e) {
            fail("Error sending message", e);
            return Optional.empty();
        }
    }

    void sendMessage(String content) throws JMSException {
        try (var producer = session.createProducer(topic)) {
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(session.createTextMessage(content));
        }
    }

    @ParameterizedTest
    @MethodSource("allVersions")
    @Timeout(value = 60)
    void versionTest(Stomp version) throws JMSException {
        try (StompClient client = StompClient.create(stomp.webSocketUrl(),
                                                     new UserCredential(stomp.username(), stomp.password()),
                                                     TransportType.WEB_SOCKET,
                                                     Set.of(version))) {
            var messageList = new ArrayList<String>();
            client.connect();
            client.subscribe(topicName, message -> messageList.add(message));
            StompTestSupport.settleSubscription();
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
            StompTestSupport.awaitMessageCount(10, messageList::size);
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

    @BeforeEach
    void setup() throws JMSException {
        var connectionFactory = new ActiveMQConnectionFactory(stomp.clientUrl());
        connection = connectionFactory.createConnection(stomp.username(), stomp.password());
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        topicName = "topic-" + UUID.randomUUID().toString();
        topic = session.createTopic(topicName);
        logger.info("Created topic: {}", topic);
    }

    @AfterEach
    void tearDown() throws JMSException {
        session.close();
        connection.close();
    }

    @ParameterizedTest
    @MethodSource("allVersions")
    @Timeout(value = 60)
    @DisplayName("Sending message with {0}")
    void sendMessageTest(Stomp version) throws Exception {
        try (var pool = Executors.newSingleThreadExecutor();
                var client = StompClient.create(stomp.webSocketUrl(), new UserCredential(stomp.username(), stomp.password()), TransportType.WEB_SOCKET,
                                                Set.of(version))) {
            client.connect();
            Future<?> sendTask = pool.submit(() -> {
                try {
                    Thread.sleep(Duration.ofMillis(100));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                client.sendPlain(topicName, "hello queue", "text/plain");
            });
            var message = receiveMessage(topicName, Duration.ofSeconds(15));
            sendTask.get(15, TimeUnit.SECONDS);
            assertThat(message).as("Verifying message for %s".formatted(version))
                               .isNotEmpty()
                               .hasValue("hello queue");
        }
    }
}
