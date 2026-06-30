package dev.vepo.stomp4j.client.tests.infra;

import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.apache.activemq.ActiveMQConnectionFactory;

import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jakarta.jms.Topic;

/**
 * <p><b>Responsibilities</b></p>
 * <ul>
 *   <li><b>Knowing:</b> The exclusive destination name bound to this fixture.</li>
 *   <li><b>Doing:</b> Open a JMS connection to Artemis (OpenWire) and publish or receive on that destination.</li>
 * </ul>
 * <p><b>Collaborators:</b> {@link StompActiveMqContainer}</p>
 * <p><b>Not responsible for:</b> STOMP client lifecycle or test assertions.</p>
 */
public final class ArtemisJmsFixture implements AutoCloseable {

    private final String destinationName;
    private final Connection connection;
    private final Session session;
    private final Topic topic;

    private ArtemisJmsFixture(String destinationName, Connection connection, Session session, Topic topic) {
        this.destinationName = destinationName;
        this.connection = connection;
        this.session = session;
        this.topic = topic;
    }

    public static ArtemisJmsFixture openTopic(StompActiveMqContainer stomp) throws JMSException {
        return openTopic(stomp, TestDestinations.uniqueTopic());
    }

    public static ArtemisJmsFixture openTopic(StompActiveMqContainer stomp, String destinationName) throws JMSException {
        var connectionFactory = new ActiveMQConnectionFactory(stomp.clientUrl());
        var connection = connectionFactory.createConnection(stomp.username(), stomp.password());
        connection.start();
        var session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        var topic = session.createTopic(destinationName);
        return new ArtemisJmsFixture(destinationName, connection, session, topic);
    }

    public String destinationName() {
        return destinationName;
    }

    public void publishText(String content) {
        try (var producer = session.createProducer(topic)) {
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(session.createTextMessage(content));
        } catch (JMSException ex) {
            fail("Error publishing JMS message", ex);
        }
    }

    public Optional<String> receiveText(Duration timeout) {
        return receiveTextAfter(timeout, () -> {});
    }

    /**
     * Creates the JMS consumer first, runs {@code afterConsumerReady}, then waits for a message.
     * Use before STOMP SEND so the topic has an active subscriber when the broker routes the message.
     */
    public Optional<String> receiveTextAfter(Duration timeout, Runnable afterConsumerReady) {
        try (var consumer = session.createConsumer(topic)) {
            var trigger = Thread.ofVirtual().name("jms-after-consumer-ready").start(afterConsumerReady);
            long deadline = System.nanoTime() + timeout.toNanos();
            Optional<String> received = Optional.empty();
            while (System.nanoTime() < deadline) {
                long remainingMs = Math.max(1L, Duration.ofNanos(deadline - System.nanoTime()).toMillis());
                var message = consumer.receive(remainingMs);
                if (Objects.nonNull(message) && message.isBodyAssignableTo(byte[].class)) {
                    received = Optional.ofNullable(new String(message.getBody(byte[].class)));
                    break;
                }
                if (Objects.nonNull(message) && message.isBodyAssignableTo(String.class)) {
                    received = Optional.ofNullable(message.getBody(String.class));
                    break;
                }
            }
            trigger.join(Math.max(1L, Duration.ofNanos(deadline - System.nanoTime()).toMillis()));
            return received;
        } catch (JMSException | InterruptedException ex) {
            if (ex instanceof InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            fail("Error receiving JMS message", ex);
            return Optional.empty();
        }
    }

    @Override
    public void close() throws JMSException {
        session.close();
        connection.close();
    }
}
