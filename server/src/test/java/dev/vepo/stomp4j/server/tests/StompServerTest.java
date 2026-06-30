package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Headers;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.SubscriberAckListener;
import dev.vepo.stomp4j.server.StompSession;
import dev.vepo.stomp4j.server.SubscriptionHandler;
import dev.vepo.stomp4j.server.tests.infra.EmbeddedServerFixture;
import dev.vepo.stomp4j.server.tests.infra.EmbeddedServerFixture.ClientTransport;
import dev.vepo.stomp4j.server.tests.infra.ServerTestSupport;
import dev.vepo.stomp4j.server.tests.infra.TestDestinations;

@Execution(ExecutionMode.CONCURRENT)
class StompServerTest {
    private record ReceivedMessage(String topic, String message) {}

    @Test
    @DisplayName("Server should reject invalid credentials")
    @Timeout(value = 10)
    void authenticationRejectionTest() {
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .authenticator(credentials -> "user".equals(credentials.username())
                                                        && "pass".equals(credentials.password()))
                                                .subscription(topic -> true)
                                                .handler(message -> {})
                                                .start();
                var client = StompClient.create(fixture.stompTcpUrl(), new UserCredential("wrong", "creds"))) {
            assertThatThrownBy(client::connect).isInstanceOf(StompException.class);
        }
    }

    @Test
    @DisplayName("A server should read a message from TCP clients")
    @Timeout(value = 10)
    void receiveMessageOverTcpTest() {
        var topic = TestDestinations.uniqueTopic();
        var receiveMessages = new LinkedList<ReceivedMessage>();
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .authenticator(credentials -> true)
                                                .subscription(t -> true)
                                                .handler(message -> receiveMessages.offer(
                                                                                      new ReceivedMessage(message.destination(), message.body())))
                                                .start();
                var client = StompClient.create(fixture.stompTcpUrl())) {
            client.connect();
            var subscription = client.subscribe(topic);
            assertThat(subscription.hasData()).isFalse();
            client.sendPlain(topic, "MESSAGE-1", "text/plain");
            await().atMost(Duration.ofSeconds(5)).until(() -> !receiveMessages.isEmpty());
            assertThat(receiveMessages).hasSize(1)
                                       .containsExactly(new ReceivedMessage(topic, "MESSAGE-1"));
        }
    }

    @Test
    @DisplayName("A server should read a message from WebSocket clients")
    @Timeout(value = 10)
    void receiveMessageOverWebSocketTest() {
        var topic = TestDestinations.uniqueTopic();
        var receiveMessages = new LinkedList<ReceivedMessage>();
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withWebSocket()
                                                .authenticator(credentials -> true)
                                                .subscription(t -> true)
                                                .handler(message -> receiveMessages.offer(
                                                                                      new ReceivedMessage(message.destination(), message.body())))
                                                .start();
                var client = StompClient.create(fixture.webSocketUrl())) {
            client.connect();
            var subscription = client.subscribe(topic);
            assertThat(subscription.hasData()).isFalse();
            client.sendPlain(topic, "MESSAGE-1", "text/plain");
            await().atMost(Duration.ofSeconds(5)).until(() -> !receiveMessages.isEmpty());
            assertThat(receiveMessages).hasSize(1)
                                       .containsExactly(new ReceivedMessage(topic, "MESSAGE-1"));
        }
    }

    @ParameterizedTest
    @EnumSource(ClientTransport.class)
    @DisplayName("A server should be able to send a message to clients")
    @Timeout(value = 10)
    void sendMessageTest(ClientTransport transport) {
        var topic = TestDestinations.uniqueTopic();
        var subscribed = new AtomicBoolean(false);
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .withWebSocket()
                                                .authenticator(credentials -> true)
                                                .subscription(t -> subscribed.compareAndSet(false, true))
                                                .handler(message -> {})
                                                .start();
                var client = StompClient.create(fixture.clientUrl(transport))) {
            client.connect();
            var subscription = client.subscribe(topic);
            assertThat(subscription.hasData()).isFalse();
            await().atMost(Duration.ofSeconds(5)).until(subscribed::get);
            fixture.outboundChannel()
                   .send(new Message(Command.SEND, Headers.builder().with(Header.DESTINATION, topic).build(), "MESSAGE-1"));
            await().atMost(Duration.ofSeconds(5)).until(subscription::hasData);
            var receiveMessages = subscription.poll();
            assertThat(receiveMessages).hasSize(1)
                                       .containsExactly("MESSAGE-1");
        }
    }

    @ParameterizedTest
    @EnumSource(ClientTransport.class)
    @DisplayName("Message handler should receive a session outbound channel")
    @Timeout(value = 10)
    void sessionReplyTest(ClientTransport transport) {
        var inboundTopic = TestDestinations.uniqueTopic("in");
        var replyTopic = TestDestinations.uniqueTopic("reply");
        var replied = new AtomicBoolean(false);
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .withWebSocket()
                                                .subscription(topic -> true)
                                                .handler(message -> {
                                                    assertThat(message.sessionChannel()).isNotNull();
                                                    message.sessionChannel()
                                                           .send(new Message(Command.MESSAGE,
                                                                             Headers.builder()
                                                                                    .with(Header.DESTINATION, replyTopic)
                                                                                    .with(Header.SUBSCRIPTION, "1")
                                                                                    .with(Header.MESSAGE_ID, "reply-1")
                                                                                    .build(),
                                                                             "REPLY-1"));
                                                    replied.set(true);
                                                })
                                                .start();
                var client = StompClient.create(fixture.clientUrl(transport))) {
            client.connect();
            var subscription = client.subscribe(replyTopic);
            client.sendPlain(inboundTopic, "ping", "text/plain");
            await().atMost(Duration.ofSeconds(5)).until(replied::get);
            await().atMost(Duration.ofSeconds(5)).until(subscription::hasData);
            assertThat(subscription.poll()).containsExactly("REPLY-1");
        }
    }

    @Test
    @DisplayName("Server should notify when subscriber acknowledges outbound message")
    @Timeout(value = 30)
    void subscriberAckListenerTest() throws InterruptedException {
        var topic = TestDestinations.uniqueTopic("ack");
        var subscribed = new AtomicBoolean(false);
        var ackReceived = new CountDownLatch(1);
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .subscription(topic -> subscribed.compareAndSet(false, true))
                                                .handler(message -> {})
                                                .start();
                var client = StompClient.create(fixture.stompTcpUrl(), (UserCredential) null, Set.of(new Stomp1_2()))) {
            client.connect();
            client.subscribe(topic, AckMode.CLIENT_INDIVIDUAL, delivery -> delivery.ack());
            await().atMost(Duration.ofSeconds(10)).until(subscribed::get);
            fixture.acknowledgedOutboundChannel()
                   .send(new Message(Command.SEND,
                                     Headers.builder().with(Header.DESTINATION, topic).build(),
                                     "ACK-ME"),
                         new SubscriberAckListener() {
                             @Override
                             public void onAck(String messageId, StompSession session) {
                                 ackReceived.countDown();
                             }

                             @Override
                             public void onNack(String messageId, StompSession session) {}
                         });
            await().atMost(Duration.ofSeconds(20)).until(() -> ackReceived.getCount() == 0);
        }
    }

    @Test
    @DisplayName("Subscription handler should receive subscribe and unsubscribe lifecycle events")
    @Timeout(value = 10)
    void subscriptionLifecycleTest() {
        var topic = TestDestinations.uniqueTopic("lifecycle");
        var subscribed = new LinkedList<String>();
        var unsubscribed = new LinkedList<String>();
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .subscription(new SubscriptionHandler() {
                                                    @Override
                                                    public boolean accept(String destination) {
                                                        return true;
                                                    }

                                                    @Override
                                                    public void onSubscribed(StompSession session, String destination) {
                                                        subscribed.add(destination);
                                                    }

                                                    @Override
                                                    public void onUnsubscribed(StompSession session, String destination) {
                                                        unsubscribed.add(destination);
                                                    }
                                                })
                                                .handler(message -> {})
                                                .start();
                var client = StompClient.create(fixture.stompTcpUrl())) {
            client.connect();
            var subscription = client.subscribe(topic);
            await().atMost(Duration.ofSeconds(5)).until(() -> !subscribed.isEmpty());
            assertThat(subscribed).containsExactly(topic);
            client.unsubscribe(subscription);
            await().atMost(Duration.ofSeconds(5)).until(() -> !unsubscribed.isEmpty());
            assertThat(unsubscribed).containsExactly(topic);
        }
    }

    @ParameterizedTest
    @EnumSource(ClientTransport.class)
    @DisplayName("Unsubscribe should stop message delivery")
    @Timeout(value = 10)
    void unsubscribeTest(ClientTransport transport) {
        var topic = TestDestinations.uniqueTopic("unsub");
        try (var fixture = EmbeddedServerFixture.builder()
                                                .withTcp()
                                                .withWebSocket()
                                                .subscription(t -> true)
                                                .handler(message -> {})
                                                .start();
                var client = StompClient.create(fixture.clientUrl(transport))) {
            client.connect();
            var subscription = client.subscribe(topic);
            client.unsubscribe(subscription);
            fixture.outboundChannel()
                   .send(new Message(Command.SEND,
                                     Headers.builder().with(Header.DESTINATION, topic).build(),
                                     "SHOULD-NOT-ARRIVE"));
            ServerTestSupport.settleFor(Duration.ofMillis(500));
            assertThat(subscription.hasData()).isFalse();
        }
    }
}
