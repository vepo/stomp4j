package dev.vepo.stomp4j.bridge.internal;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;

public final class KafkaRecordToStompMapper {

    private boolean hasContentType(ConsumerRecord<String, byte[]> record) {
        for (var header : record.headers()) {
            if ("content-type".equals(header.key())) {
                return true;
            }
        }
        return false;
    }

    public Message toStompMessage(String stompDestination, ConsumerRecord<String, byte[]> record) {
        var body = new String(record.value(), StandardCharsets.UTF_8);
        var builder = MessageBuilder.builder(Command.SEND)
                                    .header(Header.DESTINATION, stompDestination)
                                    .body(body);
        record.headers().forEach(header -> {
            var name = header.key();
            var value = new String(header.value(), StandardCharsets.UTF_8);
            if ("content-type".equals(name)) {
                builder.header(Header.CONTENT_TYPE, value);
            }
        });
        if (!hasContentType(record)) {
            builder.header(Header.CONTENT_TYPE, "text/plain");
        }
        return builder.build();
    }
}
