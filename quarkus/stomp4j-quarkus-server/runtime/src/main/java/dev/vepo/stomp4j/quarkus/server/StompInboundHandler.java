package dev.vepo.stomp4j.quarkus.server;

import dev.vepo.stomp4j.integration.server.StompDestinationHandler;
import dev.vepo.stomp4j.server.MessageHandler;
import dev.vepo.stomp4j.server.StompMessage;

public interface StompInboundHandler extends StompDestinationHandler, MessageHandler {

    @Override
    void onSend(StompMessage message);

    @Override
    boolean supports(String destination);
}
