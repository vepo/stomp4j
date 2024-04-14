package io.vepo.stomp4j.protocol.v1_1;

import io.vepo.stomp4j.port.WebSocketPort;
import io.vepo.stomp4j.protocol.Command;
import io.vepo.stomp4j.protocol.Header;
import io.vepo.stomp4j.protocol.Message;
import io.vepo.stomp4j.protocol.MessageBuilder;
import io.vepo.stomp4j.protocol.Stomp;
import io.vepo.stomp4j.protocol.StompEventListener;

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
    public void handleMessage(Message message, StompEventListener listener) {
        switch (message.command()) {
            case CONNECTED:
                listener.connected();
                break;
            case MESSAGE:
                listener.message(message);
                break;
            case ERROR:
                listener.error(message);
                break;
            default:
                break;
        }
    }

    @Override
    public void subscribe(String topic, WebSocketPort webSocket) {
        webSocket.send(MessageBuilder.builder(Command.SUBSCRIBE)
                                     .header(Header.ID, Long.toString(webSocket.nextId()))
                                     .header(Header.DESTINATION, "/topic/" + topic)
                                     .header(Header.ACK, "client")
                                     .build());
    }
}
