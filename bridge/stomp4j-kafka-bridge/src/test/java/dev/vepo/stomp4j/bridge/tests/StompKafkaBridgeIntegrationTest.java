package dev.vepo.stomp4j.bridge.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import dev.vepo.stomp4j.bridge.DestinationMapping;
import dev.vepo.stomp4j.bridge.KafkaBridgeConfig;
import dev.vepo.stomp4j.bridge.StompKafkaBridge;
import dev.vepo.stomp4j.bridge.tests.infra.KafkaTestExtension;
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.commons.TransportType;

@Tag("integration")
@ExtendWith(KafkaTestExtension.class)
class StompKafkaBridgeIntegrationTest {

    @Test
    @DisplayName("Bridge should reject STOMP authentication when configured")
    @Timeout(120)
    void bridgeShouldRejectInvalidCredentials() throws InterruptedException {
        var tcpPort = randomPort();

        try (var bridge = StompKafkaBridge.builder()
                                          .kafkaConfig(kafkaConfig())
                                          .destinationMapping(DestinationMapping.prefix("/topic/"))
                                          .channel(TransportType.TCP, tcpPort)
                                          .authenticator(credentials -> "bridge".equals(credentials.username())
                                                  && "secret".equals(credentials.password()))
                                          .start();
                var client = StompClient.create("stomp://localhost:%d".formatted(tcpPort), new UserCredential("wrong", "creds"))) {
            assertThatThrownBy(client::connect).isInstanceOf(StompException.class);
        }
    }

    private KafkaBridgeConfig kafkaConfig() {
        return KafkaBridgeConfig.builder()
                                .bootstrapServers(KafkaTestExtension.kafka().bootstrapServers())
                                .consumerGroupId("stomp4j-bridge-test-%s".formatted(UUID.randomUUID()))
                                .pollTimeoutMs(500L)
                                .build();
    }

    private KafkaConsumer<String, byte[]> kafkaConsumer(String topic) {
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestExtension.kafka().bootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "stomp4j-bridge-verify-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        var consumer = new KafkaConsumer<String, byte[]>(properties);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private KafkaProducer<String, byte[]> kafkaProducer() {
        var properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestExtension.kafka().bootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        return new KafkaProducer<>(properties);
    }

    @Test
    @DisplayName("Kafka record should be delivered to STOMP subscriber")
    @Timeout(120)
    void kafkaRecordShouldBeDeliveredToStompSubscriber() throws Exception {
        var tcpPort = randomPort();
        var topic = "events-" + UUID.randomUUID();
        var stompDestination = "/topic/" + topic;

        try (var bridge = startBridge(tcpPort);
                var stompClient = StompClient.create("stomp://localhost:%d".formatted(tcpPort))) {
            stompClient.connect();
            var subscription = stompClient.subscribe(stompDestination);
            await().atMost(Duration.ofSeconds(10)).pollDelay(Duration.ofMillis(500)).until(() -> true);

            try (var producer = kafkaProducer()) {
                producer.send(new ProducerRecord<>(topic, "event-1".getBytes(StandardCharsets.UTF_8))).get();
            }

            await().atMost(Duration.ofSeconds(30)).until(subscription::hasData);
            assertThat(subscription.poll()).containsExactly("event-1");
        }
    }

    @Test
    @DisplayName("Multiple STOMP subscribers should receive Kafka broadcast")
    @Timeout(120)
    void multipleSubscribersShouldReceiveBroadcast() throws Exception {
        var tcpPort = randomPort();
        var topic = "fanout-" + UUID.randomUUID();
        var stompDestination = "/topic/" + topic;

        try (var bridge = startBridge(tcpPort);
                var clientOne = StompClient.create("stomp://localhost:%d".formatted(tcpPort));
                var clientTwo = StompClient.create("stomp://localhost:%d".formatted(tcpPort))) {
            clientOne.connect();
            clientTwo.connect();
            var subscriptionOne = clientOne.subscribe(stompDestination);
            var subscriptionTwo = clientTwo.subscribe(stompDestination);
            await().atMost(Duration.ofSeconds(10)).pollDelay(Duration.ofMillis(500)).until(() -> true);

            try (var producer = kafkaProducer()) {
                producer.send(new ProducerRecord<>(topic, "broadcast".getBytes(StandardCharsets.UTF_8))).get();
            }

            await().atMost(Duration.ofSeconds(30)).until(() -> subscriptionOne.hasData() && subscriptionTwo.hasData());
            assertThat(subscriptionOne.poll()).containsExactly("broadcast");
            assertThat(subscriptionTwo.poll()).containsExactly("broadcast");
        }
    }

    private int randomPort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to allocate ephemeral TCP port", ex);
        }
    }

    private StompKafkaBridge startBridge(int tcpPort) {
        return StompKafkaBridge.builder()
                               .kafkaConfig(kafkaConfig())
                               .destinationMapping(DestinationMapping.prefix("/topic/"))
                               .channel(TransportType.TCP, tcpPort)
                               .start();
    }

    @Test
    @DisplayName("STOMP SEND should produce to Kafka topic")
    @Timeout(120)
    void stompSendShouldProduceToKafka() throws Exception {
        var tcpPort = randomPort();
        var topic = "orders-" + UUID.randomUUID();
        var stompDestination = "/topic/" + topic;

        try (var bridge = startBridge(tcpPort);
                var stompClient = StompClient.create("stomp://localhost:%d".formatted(tcpPort))) {
            stompClient.connect();
            stompClient.sendPlain(stompDestination, "order-1", "text/plain");

            try (var consumer = kafkaConsumer(topic)) {
                await().atMost(Duration.ofSeconds(30)).until(() -> {
                    var records = consumer.poll(Duration.ofMillis(500));
                    if (records.isEmpty()) {
                        return false;
                    }
                    var record = records.iterator().next();
                    assertThat(new String(record.value(), StandardCharsets.UTF_8)).isEqualTo("order-1");
                    return true;
                });
            }
        }
    }

    @Test
    @DisplayName("Unsubscribe should stop Kafka to STOMP delivery")
    @Timeout(120)
    void unsubscribeShouldStopDelivery() throws Exception {
        var tcpPort = randomPort();
        var topic = "unsub-" + UUID.randomUUID();
        var stompDestination = "/topic/" + topic;

        try (var bridge = startBridge(tcpPort);
                var stompClient = StompClient.create("stomp://localhost:%d".formatted(tcpPort))) {
            stompClient.connect();
            var subscription = stompClient.subscribe(stompDestination);
            stompClient.unsubscribe(subscription);

            try (var producer = kafkaProducer()) {
                producer.send(new ProducerRecord<>(topic, "after-unsub".getBytes(StandardCharsets.UTF_8))).get();
            }

            await().pollDelay(Duration.ofSeconds(2)).until(() -> true);
            assertThat(subscription.hasData()).isFalse();
        }
    }
}
