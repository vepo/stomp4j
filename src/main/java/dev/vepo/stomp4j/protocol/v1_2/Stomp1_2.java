package dev.vepo.stomp4j.protocol.v1_2;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.Subscription;
import dev.vepo.stomp4j.protocol.Command;
import dev.vepo.stomp4j.protocol.Header;
import dev.vepo.stomp4j.protocol.Message;
import dev.vepo.stomp4j.protocol.MessageBuilder;
import dev.vepo.stomp4j.protocol.Stomp;
import dev.vepo.stomp4j.protocol.Transport;

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
                transport.send(MessageBuilder.builder(Command.ACK)
                                             .headerIfPresent(Header.ID, message.headers().get(Header.MESSAGE_ID))
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
                                     .headerIfPresent(Header.SESSION, session)
                                     .build());
    }

    @Override
    public void unsubscribe(Subscription subscription, Transport transport) {
        transport.send(MessageBuilder.builder(Command.UNSUBSCRIBE)
                                     .header(Header.ID, Integer.toString(subscription.id()))
                                     .build());
    }

}
