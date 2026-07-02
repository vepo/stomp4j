package dev.vepo.stomp4j.integration.server;

import java.util.List;

import dev.vepo.stomp4j.server.MessageHandler;
import dev.vepo.stomp4j.server.StompMessage;

public final class CompositeStompDestinationHandler implements MessageHandler {

    private final List<StompDestinationHandler> handlers;

    public CompositeStompDestinationHandler(List<? extends StompDestinationHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    @Override
    public void onSend(StompMessage message) {
        handlers.stream()
                .filter(handler -> handler.supports(message.destination()))
                .forEach(handler -> handler.onSend(message));
    }

    public boolean supports(String destination) {
        return handlers.stream().anyMatch(handler -> handler.supports(destination));
    }
}
