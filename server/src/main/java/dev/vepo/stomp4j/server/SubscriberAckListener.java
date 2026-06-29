package dev.vepo.stomp4j.server;

public interface SubscriberAckListener {

    void onAck(String messageId, StompSession session);

    void onNack(String messageId, StompSession session);
}
