package dev.vepo.stomp4j.bridge.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.bridge.DestinationMapper;
import dev.vepo.stomp4j.bridge.KafkaBridgeConfig;
import dev.vepo.stomp4j.server.MessageHandler;
import dev.vepo.stomp4j.server.StompMessage;

public final class BridgeMessageHandler implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(BridgeMessageHandler.class);

    private final DestinationMapper destinationMapper;
    private final KafkaProducerFacade producer;
    private final StompToKafkaRecordMapper recordMapper;

    public BridgeMessageHandler(DestinationMapper destinationMapper,
                                KafkaProducerFacade producer,
                                StompToKafkaRecordMapper recordMapper) {
        this.destinationMapper = destinationMapper;
        this.producer = producer;
        this.recordMapper = recordMapper;
    }

    @Override
    public void onSend(StompMessage message) {
        var destination = message.destination();
        destinationMapper.toKafkaTopic(destination).ifPresentOrElse(kafkaTopic -> {
            var record = recordMapper.toRecord(kafkaTopic, message);
            producer.send(record);
            logger.debug("Produced STOMP SEND from {} to Kafka topic {}", destination, kafkaTopic);
        }, () -> logger.warn("No Kafka topic mapping for STOMP destination {}", destination));
    }
}
