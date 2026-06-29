package dev.vepo.stomp4j.samples.server;

import org.springframework.stereotype.Component;

import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Headers;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.StompMessage;
import dev.vepo.stomp4j.server.auth.Credentials;
import dev.vepo.stomp4j.server.auth.StompAuthenticator;
import dev.vepo.stomp4j.spring.boot.autoconfigure.server.StompInboundHandler;

@Component
public class ChatInboundHandler implements StompInboundHandler, StompAuthenticator {

    @Override
    public boolean authenticate(Credentials credentials) {
        return "demo".equals(credentials.username());
    }

    @Override
    public void onSend(StompMessage message) {
        var room = message.destination().substring("/app/chat/".length());
        message.sessionChannel()
               .send(new Message(Command.SEND,
                                 Headers.builder().with(Header.DESTINATION, "/topic/chat/" + room).build(),
                                 message.body()));
    }

    @Override
    public boolean supports(String destination) {
        return destination.startsWith("/app/chat/");
    }
}
