package dev.vepo.stomp4j.client.protocol.v1_2;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.Subscription;
import dev.vepo.stomp4j.client.protocol.AcknowledgementIds;
import dev.vepo.stomp4j.client.protocol.SendParameters;
import dev.vepo.stomp4j.client.protocol.Stomp;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;

public class Stomp1_2 extends Stomp {
    private static final Logger logger = LoggerFactory.getLogger(Stomp1_2.class);

    @Override
    public void acknowledge(Message message, Optional<String> session, Transport transport) {
        transport.send(MessageBuilder.builder(Command.ACK)
                                     .headerIfPresent(Header.ID, AcknowledgementIds.forStomp12(message))
                                     .build());
    }

    @Override
    public void acknowledge(Message message,
                            Optional<String> session,
                            Transport transport,
                            Optional<String> transactionId) {
        var builder = MessageBuilder.builder(Command.ACK)
                                    .headerIfPresent(Header.ID, AcknowledgementIds.forStomp12(message));
        applyTransaction(builder, transactionId);
        transport.send(builder.build());
    }

    @Override
    public boolean hasHeartBeat() {
        return true;
    }

    @Override
    public void negativeAcknowledge(Message message, Optional<String> session, Transport transport) {
        transport.send(MessageBuilder.builder(Command.NACK)
                                     .headerIfPresent(Header.ID, AcknowledgementIds.forStomp12(message))
                                     .build());
    }

    @Override
    public void negativeAcknowledge(Message message,
                                    Optional<String> session,
                                    Transport transport,
                                    Optional<String> transactionId) {
        var builder = MessageBuilder.builder(Command.NACK)
                                    .headerIfPresent(Header.ID, AcknowledgementIds.forStomp12(message));
        applyTransaction(builder, transactionId);
        transport.send(builder.build());
    }

    @Override
    public void onMessage(Message message, Optional<String> session, Transport transport) {
        logger.debug("Received protocol message: {}", message.command());
    }

    @Override
    public void send(String destination, String content, String contentType, Optional<String> session, Transport transport) {
        send(destination, content, contentType, session, transport, SendParameters.plain());
    }

    @Override
    public void send(String destination,
                     String content,
                     String contentType,
                     Optional<String> session,
                     Transport transport,
                     SendParameters parameters) {
        var builder = MessageBuilder.builder(Command.SEND)
                                    .header(Header.DESTINATION, destination)
                                    .header(Header.CONTENT_TYPE, contentType)
                                    .header(Header.CONTENT_LENGTH, Integer.toString(content.length()))
                                    .headerIfPresent(Header.SESSION, session);
        applyCustomHeaders(builder, parameters.customHeaders());
        applyTransaction(builder, parameters.transactionId());
        parameters.receiptId().ifPresent(id -> builder.header(Header.RECEIPT, id));
        transport.send(builder.body(content).build());
    }

    @Override
    public void subscribe(Subscription subscription,
                          Optional<String> session,
                          Transport transport,
                          AckMode ackMode,
                          Map<String, String> customHeaders) {
        var builder = MessageBuilder.builder(Command.SUBSCRIBE)
                                    .header(Header.ID, Integer.toString(subscription.id()))
                                    .header(Header.DESTINATION, subscription.topic())
                                    .headerIfPresent(Header.SESSION, session);
        if (ackMode != AckMode.AUTO) {
            builder.header(Header.ACK, ackMode.wireValue());
        }
        applyCustomHeaders(builder, customHeaders);
        transport.send(builder.build());
    }

    @Override
    public void unsubscribe(Subscription subscription, Transport transport) {
        transport.send(MessageBuilder.builder(Command.UNSUBSCRIBE)
                                     .header(Header.ID, Integer.toString(subscription.id()))
                                     .build());
    }

    @Override
    public String version() {
        return "1.2";
    }
}
