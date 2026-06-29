package dev.vepo.stomp4j.spring.boot.autoconfigure.server;

import dev.vepo.stomp4j.server.StompMessage;

public interface StompInboundHandler {

    boolean supports(String destination);

    void onSend(StompMessage message);
}
