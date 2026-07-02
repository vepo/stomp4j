package dev.vepo.stomp4j.integration.server;

import dev.vepo.stomp4j.server.StompMessage;

public interface StompDestinationHandler {

    void onSend(StompMessage message);

    boolean supports(String destination);
}
