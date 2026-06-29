package dev.vepo.stomp4j.spring.boot.autoconfigure.server;

import java.util.List;

import dev.vepo.stomp4j.server.MessageHandler;
import dev.vepo.stomp4j.server.StompMessage;

public class CompositeStompInboundHandler implements MessageHandler {

    private final List<StompInboundHandler> handlers;

    public CompositeStompInboundHandler(List<StompInboundHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void onSend(StompMessage message) {
        handlers.stream()
                .filter(handler -> handler.supports(message.destination()))
                .forEach(handler -> handler.onSend(message));
    }
}
