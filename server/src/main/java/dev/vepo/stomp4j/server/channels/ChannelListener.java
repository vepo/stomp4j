package dev.vepo.stomp4j.server.channels;

import dev.vepo.stomp4j.commons.protocol.Message;

public interface ChannelListener {

    void messageReceived(Message message);

    boolean subscriptionRequested(String topic);
}
