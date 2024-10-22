package dev.vepo.stomp4j;

import dev.vepo.stomp4j.protocol.Header;
import dev.vepo.stomp4j.protocol.MessageBuilder;

public record UserCredential(String username, String password) {

    public void apply(MessageBuilder builder) {
        builder.header(Header.LOGIN, this.username)
               .header(Header.PASSCODE, this.password);
    }

}
