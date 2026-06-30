package dev.vepo.stomp4j.commons.tests.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;

class CommandTest {

    @Test
    void shouldRoundTripBeginFrame() {
        var message = MessageBuilder.builder(Command.BEGIN)
                                    .header(Header.TRANSACTION, "tx-1")
                                    .build();
        var encoded = message.encode();
        var decoded = Message.readMessage(encoded.substring(0, encoded.length() - 1));
        assertThat(decoded.command()).isEqualTo(Command.BEGIN);
        assertThat(decoded.headers().get(Header.TRANSACTION)).contains("tx-1");
    }

    @Test
    void shouldRoundTripDisconnectFrame() {
        var message = MessageBuilder.builder(Command.DISCONNECT).build();
        var encoded = message.encode();
        var decoded = Message.readMessage(encoded.substring(0, encoded.length() - 1));
        assertThat(decoded.command()).isEqualTo(Command.DISCONNECT);
    }

    @Test
    void shouldRoundTripNackFrame() {
        var message = MessageBuilder.builder(Command.NACK)
                                    .header(Header.ID, "msg-1")
                                    .build();
        var encoded = message.encode();
        var decoded = Message.readMessage(encoded.substring(0, encoded.length() - 1));
        assertThat(decoded.command()).isEqualTo(Command.NACK);
        assertThat(decoded.headers().get(Header.ID)).contains("msg-1");
    }

    @Test
    void shouldRoundTripReceiptFrame() {
        var message = MessageBuilder.builder(Command.RECEIPT)
                                    .header(Header.RECEIPT_ID, "receipt-42")
                                    .build();
        var encoded = message.encode();
        var decoded = Message.readMessage(encoded.substring(0, encoded.length() - 1));
        assertThat(decoded.command()).isEqualTo(Command.RECEIPT);
        assertThat(decoded.headers().get(Header.RECEIPT_ID)).contains("receipt-42");
    }
}
