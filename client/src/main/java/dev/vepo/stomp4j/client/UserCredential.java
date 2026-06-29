package dev.vepo.stomp4j.client;

import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;

public record UserCredential(String username, String password) {

    public void apply(MessageBuilder builder) {
        builder.header(Header.LOGIN, this.username)
               .header(Header.PASSCODE, this.password);
    }

}
