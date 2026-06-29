package dev.vepo.stomp4j.quarkus.server;

import java.util.List;

import dev.vepo.stomp4j.server.StompMessage;

public final class CompositeStompInboundHandler implements StompInboundHandler {

    private final List<StompInboundHandler> handlers;

    public CompositeStompInboundHandler(List<StompInboundHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    @Override
    public void onSend(StompMessage message) {
        handlers.stream()
                .filter(handler -> handler.supports(message.destination()))
                .forEach(handler -> handler.onSend(message));
    }

    @Override
    public boolean supports(String destination) {
        return handlers.stream().anyMatch(handler -> handler.supports(destination));
    }
}
