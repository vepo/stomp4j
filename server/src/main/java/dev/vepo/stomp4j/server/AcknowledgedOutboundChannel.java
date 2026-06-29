package dev.vepo.stomp4j.server;

import dev.vepo.stomp4j.commons.protocol.Message;

public interface AcknowledgedOutboundChannel extends OutboundChannel {

    void send(Message message, SubscriberAckListener listener);
}
