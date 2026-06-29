package dev.vepo.stomp4j.bridge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class KafkaBridgeConfig {

    public static final class Builder {
        private String bootstrapServers;
        private String consumerGroupId = "stomp4j-bridge";
        private String dlqTopic;
        private long pollTimeoutMs = 1000L;
        private final Map<String, String> kafkaProperties = new HashMap<>();

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public KafkaBridgeConfig build() {
            Objects.requireNonNull(bootstrapServers, "bootstrapServers is required");
            return new KafkaBridgeConfig(this);
        }

        public Builder consumerGroupId(String consumerGroupId) {
            this.consumerGroupId = consumerGroupId;
            return this;
        }

        public Builder dlqTopic(String dlqTopic) {
            this.dlqTopic = dlqTopic;
            return this;
        }

        public Builder kafkaProperties(Map<String, String> properties) {
            kafkaProperties.putAll(properties);
            return this;
        }

        public Builder kafkaProperty(String key, String value) {
            kafkaProperties.put(key, value);
            return this;
        }

        public Builder pollTimeoutMs(long pollTimeoutMs) {
            this.pollTimeoutMs = pollTimeoutMs;
            return this;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final String bootstrapServers;
    private final String consumerGroupId;
    private final String dlqTopic;

    private final long pollTimeoutMs;

    private final Map<String, String> kafkaProperties;

    private KafkaBridgeConfig(Builder builder) {
        this.bootstrapServers = builder.bootstrapServers;
        this.consumerGroupId = builder.consumerGroupId;
        this.dlqTopic = builder.dlqTopic;
        this.pollTimeoutMs = builder.pollTimeoutMs;
        this.kafkaProperties = Map.copyOf(builder.kafkaProperties);
    }

    public String bootstrapServers() {
        return bootstrapServers;
    }

    public String consumerGroupId() {
        return consumerGroupId;
    }

    public Map<String, Object> consumerProperties() {
        return consumerPropertiesForGroup(consumerGroupId);
    }

    private Map<String, Object> consumerPropertiesForGroup(String groupId) {
        var config = new HashMap<String, Object>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        config.put("enable.auto.commit", "false");
        config.put("auto.offset.reset", "earliest");
        config.put("group.id", groupId);
        kafkaProperties.forEach(config::put);
        return Collections.unmodifiableMap(config);
    }

    public Map<String, Object> consumerPropertiesForTopic(String kafkaTopic) {
        var groupId = "%s-%s".formatted(consumerGroupId, kafkaTopic);
        return consumerPropertiesForGroup(groupId);
    }

    public String dlqTopic() {
        return dlqTopic;
    }

    public Map<String, String> kafkaProperties() {
        return kafkaProperties;
    }

    public long pollTimeoutMs() {
        return pollTimeoutMs;
    }

    public Map<String, Object> producerProperties() {
        return toKafkaConfig(false);
    }

    private Map<String, Object> toKafkaConfig(boolean consumer) {
        var config = new HashMap<String, Object>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        config.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        if (consumer) {
            config.put("group.id", consumerGroupId);
            config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            config.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
            config.put("enable.auto.commit", "false");
            config.put("auto.offset.reset", "earliest");
        }
        kafkaProperties.forEach(config::put);
        return Collections.unmodifiableMap(config);
    }
}
