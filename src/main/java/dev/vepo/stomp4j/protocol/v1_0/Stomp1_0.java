package dev.vepo.stomp4j.protocol.v1_0;

import java.util.Optional;

import dev.vepo.stomp4j.Subscription;
import dev.vepo.stomp4j.protocol.Command;
import dev.vepo.stomp4j.protocol.Header;
import dev.vepo.stomp4j.protocol.Message;
import dev.vepo.stomp4j.protocol.MessageBuilder;
import dev.vepo.stomp4j.protocol.Stomp;
import dev.vepo.stomp4j.protocol.Transport;

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
                                             .headerIfPresent(Header.MESSAGE_ID, message.headers().get(Header.MESSAGE_ID))
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
    public boolean hasHeartBeat() {
        return false;
    }

    @Override
    public void subscribe(Subscription subscription, Optional<String> session, Transport transport) {
        transport.send(MessageBuilder.builder(Command.SUBSCRIBE)
                                     .header(Header.DESTINATION, subscription.topic())
                                     .header(Header.ACK, "client")
                                     .build());
    }

    @Override
    public void unsubscribe(Subscription subscription, Transport transport) {
        transport.send(MessageBuilder.builder(Command.UNSUBSCRIBE)
                                     .header(Header.DESTINATION, subscription.topic())
                                     .build());
    }
}
