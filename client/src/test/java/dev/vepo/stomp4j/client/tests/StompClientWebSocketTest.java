package dev.vepo.stomp4j.client.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Stream;

import org.awaitility.Durations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.client.protocol.Stomp;
import dev.vepo.stomp4j.client.protocol.v1_0.Stomp1_0;
import dev.vepo.stomp4j.client.protocol.v1_1.Stomp1_1;
import dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;
import dev.vepo.stomp4j.client.tests.infra.ArtemisJmsFixture;
import dev.vepo.stomp4j.client.tests.infra.StompActiveMqContainer;
import dev.vepo.stomp4j.client.tests.infra.StompContainer;
import dev.vepo.stomp4j.client.tests.infra.StompTestSupport;

@Tag("integration")
@ExtendWith(StompContainer.class)
@Execution(ExecutionMode.SAME_THREAD)
class StompClientWebSocketTest {

    private static Stream<Arguments> allVersions() {
        return Stream.of(Arguments.of(new Stomp1_0()),
                         Arguments.of(new Stomp1_1()),
                         Arguments.of(new Stomp1_2()));
    }

    @ParameterizedTest
    @MethodSource("allVersions")
    @Timeout(value = 60)
    void versionTest(Stomp version, StompActiveMqContainer stomp) throws Exception {
        try (var jms = ArtemisJmsFixture.openTopic(stomp);
                StompClient client = StompClient.create(stomp.webSocketUrl(),
                                                     new UserCredential(stomp.username(), stomp.password()),
                                                     TransportType.WEB_SOCKET,
                                                     Set.of(version))) {
            var topicName = jms.destinationName();
            var messageList = StompTestSupport.threadSafeMessageList();
            client.connect();
            client.subscribe(topicName, messageList::add);
            StompTestSupport.settleSubscription();
            jms.publishText("message-01");
            jms.publishText("message-02");
            jms.publishText("message-03");
            jms.publishText("message-04");
            jms.publishText("message-05");
            jms.publishText("message-06");
            jms.publishText("message-07");
            jms.publishText("message-08");
            jms.publishText("message-09");
            jms.publishText("message-10");
            StompTestSupport.awaitMessageCount(10, messageList::size);
            assertThat(messageList).containsExactly("message-01", "message-02", "message-03", "message-04",
                                                    "message-05", "message-06", "message-07", "message-08",
                                                    "message-09", "message-10");
            client.unsubscribe(topicName);
            jms.publishText("message-11");
            StompTestSupport.settleFor(Durations.ONE_SECOND);
            assertThat(messageList).hasSize(10);
            jms.publishText("message-12");
            assertThat(messageList).hasSize(10);
            jms.publishText("message-13");
            assertThat(messageList).hasSize(10);
        }
    }

    @ParameterizedTest
    @MethodSource("allVersions")
    @Timeout(value = 60)
    @DisplayName("Sending message with {0}")
    void sendMessageTest(Stomp version, StompActiveMqContainer stomp) throws Exception {
        try (var jms = ArtemisJmsFixture.openTopic(stomp);
                var client = StompClient.create(stomp.webSocketUrl(),
                                                new UserCredential(stomp.username(), stomp.password()),
                                                TransportType.WEB_SOCKET,
                                                Set.of(version))) {
            var topicName = jms.destinationName();
            client.connect();
            var message = jms.receiveTextAfter(Duration.ofSeconds(15), () -> {
                try {
                    Thread.sleep(Duration.ofMillis(100));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                client.sendPlain(topicName, "hello queue", "text/plain");
            });
            assertThat(message).as("Verifying message for %s".formatted(version))
                               .isNotEmpty()
                               .hasValue("hello queue");
        }
    }
}
