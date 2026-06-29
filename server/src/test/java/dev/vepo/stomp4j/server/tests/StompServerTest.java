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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Headers;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;
import dev.vepo.stomp4j.server.StompServer;
import dev.vepo.stomp4j.server.SubscriberAckListener;
import dev.vepo.stomp4j.server.StompSession;
import dev.vepo.stomp4j.server.SubscriptionHandler;

class StompServerTest {
    private record ReceivedMessage(String topic, String message) {}

    @ParameterizedTest
    @ValueSource(strings = { "stomp://localhost:5504" })
    @DisplayName("Server should reject invalid credentials")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void authenticationRejectionTest(String url) {
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5504)
                                     .authenticator(credentials -> "user".equals(credentials.username())
                                             && "pass".equals(credentials.password()))
                                     .subscription(topic -> true)
                                     .handler(message -> {})
                                     .start();
                var client = StompClient.create(url, new UserCredential("wrong", "creds"))) {
            assertThatThrownBy(client::connect).isInstanceOf(StompException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "stomp://localhost:5500", "ws://localhost:5501" })
    @DisplayName("A server should be able to read a message from clients")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void receiveMessageTest(String url) {
        var receiveMessages = new LinkedList<ReceivedMessage>();
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5500)
                                     .channel(TransportType.WEB_SOCKET, 5501)
                                     .authenticator(credentials -> true)
                                     .subscription(topic -> true)
                                     .handler(message -> receiveMessages.offer(
                                                                               new ReceivedMessage(message.destination(), message.body())))
                                     .start();
                var tcpClient = StompClient.create(url)) {
            tcpClient.connect();
            var subscription = tcpClient.subscribe("topic-1");
            assertThat(subscription.hasData()).isFalse();
            tcpClient.sendPlain("topic-1", "MESSAGE-1", "text/plain");
            await().until(() -> !receiveMessages.isEmpty());
            assertThat(receiveMessages).hasSize(1)
                                       .containsExactly(new ReceivedMessage("topic-1", "MESSAGE-1"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "stomp://localhost:5500", "ws://localhost:5501" })
    @DisplayName("A server should be able to send a message to clients")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void sendMessageTest(String url) {
        var subscribed = new AtomicBoolean(false);
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5500)
                                     .channel(TransportType.WEB_SOCKET, 5501)
                                     .authenticator(credentials -> true)
                                     .subscription(topic -> subscribed.compareAndSet(false, true))
                                     .handler(message -> {})
                                     .start();
                var tcpClient = StompClient.create(url)) {
            tcpClient.connect();
            var subscription = tcpClient.subscribe("topic-1");
            assertThat(subscription.hasData()).isFalse();
            await().until(subscribed::get);
            server.outboundChannel()
                  .send(new Message(Command.SEND, Headers.builder().with(Header.DESTINATION, "topic-1").build(), "MESSAGE-1"));
            await().until(() -> subscription.hasData());
            var receiveMessages = subscription.poll();
            assertThat(receiveMessages).hasSize(1)
                                       .containsExactly("MESSAGE-1");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "stomp://localhost:5502", "ws://localhost:5503" })
    @DisplayName("Message handler should receive a session outbound channel")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void sessionReplyTest(String url) {
        var replied = new AtomicBoolean(false);
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5502)
                                     .channel(TransportType.WEB_SOCKET, 5503)
                                     .subscription(topic -> true)
                                     .handler(message -> {
                                         assertThat(message.sessionChannel()).isNotNull();
                                         message.sessionChannel()
                                                .send(new Message(Command.MESSAGE,
                                                                  Headers.builder()
                                                                         .with(Header.DESTINATION, "topic-reply")
                                                                         .with(Header.SUBSCRIPTION, "1")
                                                                         .with(Header.MESSAGE_ID, "reply-1")
                                                                         .build(),
                                                                  "REPLY-1"));
                                         replied.set(true);
                                     })
                                     .start();
                var client = StompClient.create(url)) {
            client.connect();
            var subscription = client.subscribe("topic-reply");
            client.sendPlain("topic-in", "ping", "text/plain");
            await().until(replied::get);
            await().until(() -> subscription.hasData());
            assertThat(subscription.poll()).containsExactly("REPLY-1");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "stomp://localhost:5507" })
    @DisplayName("Server should notify when subscriber acknowledges outbound message")
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void subscriberAckListenerTest(String url) throws InterruptedException {
        var subscribed = new AtomicBoolean(false);
        var ackReceived = new CountDownLatch(1);
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5507)
                                     .subscription(topic -> subscribed.compareAndSet(false, true))
                                     .handler(message -> {})
                                     .start();
                var client = StompClient.create(url, (UserCredential) null, Set.of(new Stomp1_2()))) {
            client.connect();
            client.subscribe("topic-ack", AckMode.CLIENT_INDIVIDUAL, delivery -> delivery.ack());
            await().atMost(Duration.ofSeconds(10)).until(subscribed::get);
            server.acknowledgedOutboundChannel()
                  .send(new Message(Command.SEND,
                                    Headers.builder().with(Header.DESTINATION, "topic-ack").build(),
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

    @ParameterizedTest
    @ValueSource(strings = { "stomp://localhost:5508" })
    @DisplayName("Subscription handler should receive subscribe and unsubscribe lifecycle events")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void subscriptionLifecycleTest(String url) {
        var subscribed = new LinkedList<String>();
        var unsubscribed = new LinkedList<String>();
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5508)
                                     .subscription(new SubscriptionHandler() {
                                         @Override
                                         public boolean accept(String topic) {
                                             return true;
                                         }

                                         @Override
                                         public void onSubscribed(StompSession session, String topic) {
                                             subscribed.add(topic);
                                         }

                                         @Override
                                         public void onUnsubscribed(StompSession session, String topic) {
                                             unsubscribed.add(topic);
                                         }
                                     })
                                     .handler(message -> {})
                                     .start();
                var client = StompClient.create(url)) {
            client.connect();
            var subscription = client.subscribe("topic-lifecycle");
            await().until(() -> !subscribed.isEmpty());
            assertThat(subscribed).containsExactly("topic-lifecycle");
            client.unsubscribe(subscription);
            await().until(() -> !unsubscribed.isEmpty());
            assertThat(unsubscribed).containsExactly("topic-lifecycle");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "stomp://localhost:5505", "ws://localhost:5506" })
    @DisplayName("Unsubscribe should stop message delivery")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void unsubscribeTest(String url) {
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5505)
                                     .channel(TransportType.WEB_SOCKET, 5506)
                                     .subscription(topic -> true)
                                     .handler(message -> {})
                                     .start();
                var client = StompClient.create(url)) {
            client.connect();
            var subscription = client.subscribe("topic-unsub");
            client.unsubscribe(subscription);
            server.outboundChannel()
                  .send(new Message(Command.SEND,
                                    Headers.builder().with(Header.DESTINATION, "topic-unsub").build(),
                                    "SHOULD-NOT-ARRIVE"));
            await().pollDelay(java.time.Duration.ofMillis(500)).until(() -> true);
            assertThat(subscription.hasData()).isFalse();
        }
    }
}
