package dev.vepo.stomp4j.quarkus.server;

import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Headers;
import dev.vepo.stomp4j.commons.protocol.Message;

public record StompServerMessage(Message frame) {

    public static StompServerMessage to(String destination, String body) {
        return new StompServerMessage(new Message(
                                                  Command.SEND,
                                                  Headers.builder().with(Header.DESTINATION, destination).build(),
                                                  body));
    }
}
