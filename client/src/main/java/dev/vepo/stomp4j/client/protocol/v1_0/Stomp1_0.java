package dev.vepo.stomp4j.client.protocol.v1_0;

import java.util.Optional;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.Subscription;
import dev.vepo.stomp4j.client.protocol.Stomp;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;

public class Stomp1_0 extends Stomp {

    @Override
    public void acknowledge(Message message, Optional<String> session, Transport transport) {
        transport.send(MessageBuilder.builder(Command.ACK)
                                     .headerIfPresent(Header.MESSAGE_ID, message.headers().get(Header.MESSAGE_ID))
                                     .build());
    }

    @Override
    public boolean hasHeartBeat() {
        return false;
    }

    @Override
    public void negativeAcknowledge(Message message, Optional<String> session, Transport transport) {
        transport.send(MessageBuilder.builder(Command.NACK)
                                     .headerIfPresent(Header.MESSAGE_ID, message.headers().get(Header.MESSAGE_ID))
                                     .build());
    }

    @Override
    public void onMessage(Message message, Optional<String> session, Transport transport) {
        // acknowledgement is handled by StompClientImpl
    }

    @Override
    public void send(String destination, String content, String contentType, Optional<String> session, Transport transport) {
        transport.send(MessageBuilder.builder(Command.SEND)
                                     .header(Header.DESTINATION, destination)
                                     .header(Header.CONTENT_LENGTH, Integer.toString(content.length()))
                                     .headerIfPresent(Header.SESSION, session)
                                     .body(content)
                                     .build());
    }

    @Override
    public void send(String destination,
                     String content,
                     String contentType,
                     Optional<String> session,
                     Transport transport,
                     Optional<String> receiptId) {
        var builder = MessageBuilder.builder(Command.SEND)
                                    .header(Header.DESTINATION, destination)
                                    .header(Header.CONTENT_LENGTH, Integer.toString(content.length()))
                                    .headerIfPresent(Header.SESSION, session);
        receiptId.ifPresent(id -> builder.header(Header.RECEIPT, id));
        transport.send(builder.body(content).build());
    }

    @Override
    public void subscribe(Subscription subscription, Optional<String> session, Transport transport, AckMode ackMode) {
        var builder = MessageBuilder.builder(Command.SUBSCRIBE)
                                    .header(Header.DESTINATION, subscription.topic());
        if (ackMode != AckMode.AUTO) {
            builder.header(Header.ACK, ackMode.wireValue());
        }
        transport.send(builder.build());
    }

    @Override
    public String toString() {
        return "Stomp 1.0 Implementation";
    }

    @Override
    public void unsubscribe(Subscription subscription, Transport transport) {
        transport.send(MessageBuilder.builder(Command.UNSUBSCRIBE)
                                     .header(Header.DESTINATION, subscription.topic())
                                     .build());
    }

    @Override
    public String version() {
        return "1.0";
    }
}
