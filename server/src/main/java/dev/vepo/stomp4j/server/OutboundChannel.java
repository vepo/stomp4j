package dev.vepo.stomp4j.server;

import dev.vepo.stomp4j.commons.protocol.Message;

public interface OutboundChannel {
    void send(Message message);
}
