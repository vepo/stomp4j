package dev.vepo.stomp4j.server;

public interface MessageHandler {
    void process(String topic, String message, OutboundChannel channel);
}
