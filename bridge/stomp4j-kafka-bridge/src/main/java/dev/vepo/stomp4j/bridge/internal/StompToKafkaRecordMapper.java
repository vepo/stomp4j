package dev.vepo.stomp4j.bridge.internal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.server.StompMessage;

public final class StompToKafkaRecordMapper {
    private static final String KAFKA_KEY_HEADER = "kafka-key";

    private List<RecordHeader> mapHeaders(StompMessage message) {
        var headers = new ArrayList<RecordHeader>();
        message.headers().get(Header.CONTENT_TYPE).ifPresent(value -> headers.add(toHeader("content-type", value)));
        return headers;
    }

    private Optional<String> resolveKey(StompMessage message) {
        return message.headers().get(KAFKA_KEY_HEADER);
    }

    private RecordHeader toHeader(String name, String value) {
        return new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8));
    }

    public ProducerRecord<String, byte[]> toRecord(String kafkaTopic, StompMessage message) {
        var body = Objects.requireNonNullElse(message.body(), "");
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        var key = resolveKey(message).orElse(null);
        var record = new ProducerRecord<>(kafkaTopic, key, bytes);
        mapHeaders(message).forEach(header -> record.headers().add(header));
        return record;
    }
}
