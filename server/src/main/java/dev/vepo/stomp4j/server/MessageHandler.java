package dev.vepo.stomp4j.server;

public interface MessageHandler {

    void onSend(StompMessage message);
}
