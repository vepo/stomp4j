package dev.vepo.stomp4j.server.channels;

import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.session.Session;

public interface ChannelListener {

    void inboundMessageReceived(Session session, Message message);

    void sessionConnected(Session session);

    void sessionDisconnected(Session session);

    boolean subscriptionRequested(Session session, String topic);
}
