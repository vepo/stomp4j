package io.vepo.stomp4j.protocol.v1_1;

import java.util.Optional;

import io.vepo.stomp4j.protocol.Command;
import io.vepo.stomp4j.protocol.Header;
import io.vepo.stomp4j.protocol.Message;
import io.vepo.stomp4j.protocol.MessageBuilder;
import io.vepo.stomp4j.protocol.Stomp;
import io.vepo.stomp4j.protocol.StompListener;
import io.vepo.stomp4j.protocol.Transport;

public class Stomp1_1 extends Stomp {

    @Override
    public String version() {
        return "1.1";
    }

    @Override
    public boolean hasHeartBeat() {
        return true;
    }

    @Override
    public boolean shouldAcknowledge() {
        return false;
    }

    @Override
    public String acknowledge(Message message) {
        return null;
    }

    @Override
    public void onMessage(Message message, Optional<String> session, Transport transport) {
        switch (message.command()) {
            case CONNECTED:
                // do nothing 
            case MESSAGE:
                //listener.message(message);
                break;
            case ERROR:
               // listener.error(message);
                break;
            default:
                break;
        }
    }

    @Override
    public void subscribe(String topic, Optional<String> session, Transport transport) {
        transport.send(MessageBuilder.builder(Command.SUBSCRIBE)
                                     .header(Header.ID, Long.toString(transport.nextId()))
                                     .header(Header.DESTINATION, topic)
                                     .header(Header.ACK, "client")
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
