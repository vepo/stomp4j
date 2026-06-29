package dev.vepo.stomp4j.client.transport;

import dev.vepo.stomp4j.commons.protocol.Message;

public interface TransportListener {
    void onConnected(Transport transport);

    void onError(Message message);

    void onMessage(Message message);
}
