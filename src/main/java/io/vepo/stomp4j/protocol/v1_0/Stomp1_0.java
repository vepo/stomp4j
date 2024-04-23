package io.vepo.stomp4j.protocol.v1_0;

import java.util.Optional;

import io.vepo.stomp4j.protocol.Command;
import io.vepo.stomp4j.protocol.Header;
import io.vepo.stomp4j.protocol.Message;
import io.vepo.stomp4j.protocol.MessageBuilder;
import io.vepo.stomp4j.protocol.Stomp;
import io.vepo.stomp4j.protocol.Transport;

public class Stomp1_0 extends Stomp {

    @Override
    public String version() {
        return "1.0";
    }

    @Override
    public void onMessage(Message message, Optional<String> session, Transport transport) {
        switch (message.command()) {
            case CONNECTED:
                // do nothin
            case MESSAGE:
                transport.send(MessageBuilder.builder(Command.ACK)
                                             .header(Header.MESSAGE_ID, message.headers().get(Header.MESSAGE_ID).orElse(""))
                                             .build());
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
        return false;
    }

    @Override
    public String acknowledge(Message message) {
        return null;
    }

    @Override
    public boolean hasHeartBeat() {
        return false;
    }

    @Override
    public void subscribe(String topic, Optional<String> session, Transport transport) {
        var builder = MessageBuilder.builder(Command.SUBSCRIBE)
                                    .header(Header.DESTINATION, topic)
                                    .header(Header.ACK, "client");
        transport.send(session.map(s -> builder.header(Header.SESSION, s)).orElse(builder).build());
    }

    // @Override
    // public void unsubscribe(String topic, WebSocketPort webSocket) {
    // webSocket.send(MessageBuilder.builder(Command.UNSUBSCRIBE)
    // .header(Header.DESTINATION, "/topic/" + topic)
    // .build());
    //
    // }

}
