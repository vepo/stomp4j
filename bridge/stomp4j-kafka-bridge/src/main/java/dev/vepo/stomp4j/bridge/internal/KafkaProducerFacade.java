package dev.vepo.stomp4j.bridge.internal;

import java.util.Objects;
import java.util.Optional;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.bridge.KafkaBridgeConfig;

public final class KafkaProducerFacade implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerFacade.class);

    private final KafkaProducer<String, byte[]> producer;
    private final Optional<String> dlqTopic;

    public KafkaProducerFacade(KafkaBridgeConfig config) {
        this.producer = new KafkaProducer<>(config.producerProperties());
        this.dlqTopic = Optional.ofNullable(config.dlqTopic()).filter(topic -> !topic.isBlank());
    }

    @Override
    public void close() {
        producer.close();
    }

    private void forwardToDlq(String topic, ProducerRecord<String, byte[]> record, Exception exception) {
        try {
            producer.send(new ProducerRecord<>(topic, record.key(), record.value()));
            logger.warn("Forwarded failed record to DLQ topic {}", topic);
        } catch (Exception dlqException) {
            logger.error("Failed to forward record to DLQ topic {}", topic, dlqException);
        }
    }

    public void send(ProducerRecord<String, byte[]> record) {
        producer.send(record, (metadata, exception) -> {
            if (Objects.nonNull(exception)) {
                logger.error("Failed to produce to topic {}", record.topic(), exception);
                dlqTopic.ifPresent(topic -> forwardToDlq(topic, record, exception));
            }
        });
    }
}
