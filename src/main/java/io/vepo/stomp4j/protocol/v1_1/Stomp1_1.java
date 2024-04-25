package io.vepo.stomp4j.protocol.v1_1;

import java.util.Optional;

import io.vepo.stomp4j.Subscription;
import io.vepo.stomp4j.protocol.Command;
import io.vepo.stomp4j.protocol.Header;
import io.vepo.stomp4j.protocol.Message;
import io.vepo.stomp4j.protocol.MessageBuilder;
import io.vepo.stomp4j.protocol.Stomp;
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
    public void onMessage(Message message, Optional<String> session, Transport transport) {
        switch (message.command()) {
            case CONNECTED:
                // do nothing
            case MESSAGE:
                transport.send(MessageBuilder.builder(Command.ACK)
                                             .headerIfPresent(Header.SUBSCRIPTION, message.headers().get(Header.SUBSCRIPTION))
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
    public void subscribe(Subscription subscription, Optional<String> session, Transport transport) {
        transport.send(MessageBuilder.builder(Command.SUBSCRIBE)
                                     .header(Header.ID, Integer.toString(subscription.id()))
                                     .header(Header.DESTINATION, subscription.topic())
                                     .header(Header.ACK, "client")
                                     .build());
    }

    @Override
    public void unsubscribe(Subscription subscription, Transport transport) {
        transport.send(MessageBuilder.builder(Command.UNSUBSCRIBE)
                                     .header(Header.ID, Integer.toString(subscription.id()))
                                     .build());
    }
}
