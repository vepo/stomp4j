package io.vepo.stomp4j.protocol;

public record Message (Command command, Headers headers, String payload) {

    public Message(Command command, Headers headers) {
        this(command, headers, null);
    }
}
