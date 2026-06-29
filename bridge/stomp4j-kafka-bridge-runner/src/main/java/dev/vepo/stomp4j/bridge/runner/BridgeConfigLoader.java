package dev.vepo.stomp4j.bridge.runner;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import dev.vepo.stomp4j.bridge.DestinationMapper;
import dev.vepo.stomp4j.bridge.DestinationMapping;
import dev.vepo.stomp4j.bridge.KafkaBridgeConfig;
import dev.vepo.stomp4j.bridge.StompKafkaBridge;
import dev.vepo.stomp4j.commons.TransportType;

public final class BridgeConfigLoader {

    private static Map<String, String> explicitMappings(Properties properties) {
        var mappings = new HashMap<String, String>();
        properties.forEach((key, value) -> {
            var propertyKey = key.toString();
            if (propertyKey.startsWith("bridge.mapping.")) {
                mappings.put(propertyKey.substring("bridge.mapping.".length()), value.toString());
            }
        });
        return mappings;
    }

    private static boolean isReservedKafkaEnv(String key) {
        return "KAFKA_BOOTSTRAP_SERVERS".equals(key)
                || "KAFKA_CONSUMER_GROUP_ID".equals(key)
                || "KAFKA_POLL_TIMEOUT_MS".equals(key);
    }

    private static Map<String, String> kafkaProperties(Properties properties) {
        var kafkaProperties = new HashMap<String, String>();
        properties.forEach((key, value) -> {
            var propertyKey = key.toString();
            if (propertyKey.startsWith("bridge.kafka.property.")) {
                kafkaProperties.put(propertyKey.substring("bridge.kafka.property.".length()), value.toString());
            } else if (propertyKey.startsWith("KAFKA_") && !isReservedKafkaEnv(propertyKey)) {
                kafkaProperties.put(propertyKey.substring("KAFKA_".length()).toLowerCase().replace('_', '.'), value.toString());
            }
        });
        return kafkaProperties;
    }

    public static StompKafkaBridge load() {
        var properties = loadProperties();
        var kafkaBuilder = KafkaBridgeConfig.builder()
                                            .bootstrapServers(required(properties, "KAFKA_BOOTSTRAP_SERVERS", "bridge.kafka.bootstrap-servers"))
                                            .consumerGroupId(property(properties,
                                                                      "KAFKA_CONSUMER_GROUP_ID",
                                                                      "bridge.kafka.consumer-group-id",
                                                                      "stomp4j-bridge"))
                                            .pollTimeoutMs(Long.parseLong(property(properties,
                                                                                   "KAFKA_POLL_TIMEOUT_MS",
                                                                                   "bridge.kafka.poll-timeout-ms",
                                                                                   "1000")));
        var dlqTopic = property(properties, "BRIDGE_DLQ_TOPIC", "bridge.dlq.topic", null);
        if (Objects.nonNull(dlqTopic) && !dlqTopic.isBlank()) {
            kafkaBuilder.dlqTopic(dlqTopic);
        }
        kafkaProperties(properties).forEach(kafkaBuilder::kafkaProperty);

        var topicPrefixStrip = property(properties, "TOPIC_PREFIX_STRIP", "bridge.topic.prefix-strip", "/topic/");
        var destinationPrefix = property(properties, "DESTINATION_PREFIX", "bridge.destination.prefix", topicPrefixStrip);
        var explicitMappings = explicitMappings(properties);

        DestinationMapper destinationMapper = explicitMappings.isEmpty()
                                                                         ? DestinationMapping.prefix(topicPrefixStrip)
                                                                         : DestinationMapping.composite(DestinationMapping.explicit(explicitMappings),
                                                                                                        DestinationMapping.prefix(topicPrefixStrip));

        var bridgeBuilder = StompKafkaBridge.builder()
                                            .kafkaConfig(kafkaBuilder.build())
                                            .destinationMapping(destinationMapper)
                                            .allowedDestinationPrefix(destinationPrefix)
                                            .serverName(property(properties, "STOMP_SERVER_NAME", "bridge.stomp.server-name", "stomp4j-kafka-bridge"))
                                            .heartbeat(Duration.ofSeconds(Long.parseLong(property(properties,
                                                                                                  "STOMP_HEARTBEAT_SECONDS",
                                                                                                  "bridge.stomp.heartbeat-seconds",
                                                                                                  "30"))))
                                            .channel(TransportType.TCP,
                                                     Integer.parseInt(property(properties, "STOMP_TCP_PORT", "bridge.stomp.tcp-port", "61613")))
                                            .channel(TransportType.WEB_SOCKET,
                                                     Integer.parseInt(property(properties, "STOMP_WS_PORT", "bridge.stomp.ws-port", "61614")));

        var username = property(properties, "STOMP_AUTH_USERNAME", "bridge.stomp.auth.username", null);
        var password = property(properties, "STOMP_AUTH_PASSWORD", "bridge.stomp.auth.password", null);
        if (Objects.nonNull(username) && !username.isBlank()) {
            var expectedPassword = Objects.requireNonNullElse(password, "");
            bridgeBuilder.authenticator(credentials -> username.equals(credentials.username())
                    && expectedPassword.equals(credentials.password()));
        }

        return bridgeBuilder.start();
    }

    private static Properties loadProperties() {
        var properties = new Properties();
        try (InputStream stream = BridgeConfigLoader.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (Objects.nonNull(stream)) {
                properties.load(stream);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return properties;
    }

    private static String property(Properties properties, String envKey, String propertyKey, String defaultValue) {
        var envValue = System.getenv(envKey);
        if (Objects.nonNull(envValue)) {
            return envValue;
        }
        return properties.getProperty(propertyKey, defaultValue);
    }

    private static String required(Properties properties, String envKey, String propertyKey) {
        var value = property(properties, envKey, propertyKey, null);
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException("Missing required configuration: %s / %s".formatted(envKey, propertyKey));
        }
        return value;
    }

    private BridgeConfigLoader() {}
}
