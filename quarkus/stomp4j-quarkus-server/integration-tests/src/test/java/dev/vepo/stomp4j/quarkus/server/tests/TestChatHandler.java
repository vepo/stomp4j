package dev.vepo.stomp4j.quarkus.server.tests;

import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Headers;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.StompMessage;
import dev.vepo.stomp4j.server.StompServer;
import dev.vepo.stomp4j.server.auth.Credentials;
import dev.vepo.stomp4j.server.auth.StompAuthenticator;
import dev.vepo.stomp4j.quarkus.server.StompInboundHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TestChatHandler implements StompInboundHandler, StompAuthenticator {

    private final StompServer server;

    @Inject
    public TestChatHandler(StompServer server) {
        this.server = server;
    }

    @Override
    public boolean authenticate(Credentials credentials) {
        return "demo".equals(credentials.username());
    }

    @Override
    public void onSend(StompMessage message) {
        var room = message.destination().substring("/app/chat/".length());
        server.outboundChannel()
              .send(new Message(Command.SEND,
                                Headers.builder().with(Header.DESTINATION, "/topic/chat/" + room).build(),
                                message.body()));
    }

    @Override
    public boolean supports(String destination) {
        return destination.startsWith("/app/chat/");
    }
}
