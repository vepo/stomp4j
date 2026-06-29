package dev.vepo.stomp4j.quarkus.server;

import dev.vepo.stomp4j.server.MessageHandler;
import dev.vepo.stomp4j.server.StompMessage;

public interface StompInboundHandler extends MessageHandler {

    @Override
    void onSend(StompMessage message);

    boolean supports(String destination);
}
