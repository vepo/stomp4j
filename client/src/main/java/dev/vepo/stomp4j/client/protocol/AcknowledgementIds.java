package dev.vepo.stomp4j.client.protocol;

import java.util.Optional;

import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;

public final class AcknowledgementIds {

    private AcknowledgementIds() {}

    public static Optional<String> forStomp12(Message message) {
        return message.headers()
                      .get(Header.ACK)
                      .or(() -> message.headers().get(Header.MESSAGE_ID));
    }

    public static Optional<String> deliveryMessageId(Message message, String protocolVersion) {
        if ("1.2".equals(protocolVersion)) {
            return forStomp12(message).or(() -> message.headers().get(Header.ID));
        }
        return message.headers()
                      .get(Header.MESSAGE_ID)
                      .or(() -> message.headers().get(Header.ID));
    }
}
