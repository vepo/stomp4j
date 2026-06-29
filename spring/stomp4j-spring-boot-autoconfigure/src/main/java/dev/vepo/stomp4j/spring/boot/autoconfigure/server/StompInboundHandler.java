package dev.vepo.stomp4j.spring.boot.autoconfigure.server;

import dev.vepo.stomp4j.server.StompMessage;

public interface StompInboundHandler {

    void onSend(StompMessage message);

    boolean supports(String destination);
}
