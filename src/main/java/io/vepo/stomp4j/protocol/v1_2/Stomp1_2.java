package io.vepo.stomp4j.protocol.v1_2;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vepo.stomp4j.protocol.Command;
import io.vepo.stomp4j.protocol.Header;
import io.vepo.stomp4j.protocol.Message;
import io.vepo.stomp4j.protocol.MessageBuilder;
import io.vepo.stomp4j.protocol.Stomp;
import io.vepo.stomp4j.protocol.StompListener;
import io.vepo.stomp4j.protocol.Transport;

public class Stomp1_2 extends Stomp {
    private static Logger logger = LoggerFactory.getLogger(Stomp1_2.class);

    @Override
    public String version() {
        return "1.2";
    }

    @Override
    public boolean hasHeartBeat() {
        return true;
    }

    @Override
    public void onMessage(Message message, Optional<String> session, Transport transport) {
        logger.info("Handling message: {}", message);
        switch (message.command()) {
            case CONNECTED:
                // do nothing
            case MESSAGE:
                // listener.message(message);
                break;
            case ERROR:
                // listener.error(message);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean shouldAcknowledge() {
        return true;
    }

    @Override
    public String acknowledge(Message message) {
        return message.headers()
                      .get(Header.MESSAGE_ID)
                      .map(messageId -> MessageBuilder.builder(Command.ACK)
                                                      .header(Header.ID, messageId)
                                                      .build())
                      .orElse(null);
    }

    @Override
    public void subscribe(String topic, Optional<String> session, Transport transport) {
        var builder = MessageBuilder.builder(Command.SUBSCRIBE)
                                    .header(Header.ID, Long.toString(transport.nextId()))
                                    .header(Header.DESTINATION, topic)
                                    .header(Header.ACK, "client");
        transport.send(session.map(s -> builder.header(Header.SESSION, s))
                              .orElse(builder)
                              .build());
    }

    // @Override
    // public void unsubscribe(String topic, WebSocketPort webSocket) {
    // webSocket.send(MessageBuilder.builder(Command.UNSUBSCRIBE)
    // .header(Header.ID, Long.toString(webSocket.nextId()))
    // .header(Header.DESTINATION, "/topic/" + topic)
    // .build());
    // }

}
