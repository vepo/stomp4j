package dev.vepo.stomp4j.commons.tests.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;
import dev.vepo.stomp4j.commons.protocol.MessageBuffer;

class FrameDecoderTest {

    @Test
    void shouldDecodeBodyUsingContentLengthWithEmbeddedNewlines() {
        var body = "line1\nline2";
        var frame = "MESSAGE\ndestination:/topic/test\nmessage-id:1\ncontent-type:text/plain\ncontent-length:%d\n\n%s"
                                                                                                                      .formatted(body.getBytes(StandardCharsets.UTF_8).length,
                                                                                                                                 body);
        var decoded = Message.readMessage(frame);
        assertThat(decoded.body()).isEqualTo(body);
    }

    @Test
    void shouldDecodeCrlfFrame() {
        var frame = "CONNECTED\r\nversion:1.2\r\n\r\n\u0000";
        var decoded = Message.readMessage(frame.substring(0, frame.length() - 1));
        assertThat(decoded.command()).isEqualTo(Command.CONNECTED);
        assertThat(decoded.headers().get(Header.VERSION)).contains("1.2");
    }

    @Test
    void shouldDecodeEscapedHeadersOnSendFrame() {
        var message = MessageBuilder.builder(Command.SEND)
                                    .header("custom", "a:b")
                                    .header(Header.DESTINATION, "/queue/a")
                                    .body("x")
                                    .build();
        var encoded = message.encode();
        assertThat(encoded).contains("custom:a\\cb");
        var decoded = Message.readMessage(encoded.substring(0, encoded.length() - 1));
        assertThat(decoded.headers().get("custom")).contains("a:b");
    }

    @Test
    void shouldEncodeContentLengthAutomatically() {
        var message = MessageBuilder.builder(Command.SEND)
                                    .header(Header.DESTINATION, "/queue/a")
                                    .header(Header.CONTENT_TYPE, "text/plain")
                                    .body("hello")
                                    .build();
        var encoded = message.encode();
        assertThat(encoded).contains("content-length:5");
        var decoded = Message.readMessage(encoded.substring(0, encoded.length() - 1));
        assertThat(decoded.body()).isEqualTo("hello");
    }

    @Test
    void shouldParseMultipleFramesFromByteBuffer() {
        var buffer = new MessageBuffer();
        var first = MessageBuilder.builder(Command.MESSAGE)
                                  .header(Header.DESTINATION, "/topic/1")
                                  .header(Header.MESSAGE_ID, "1")
                                  .body("one")
                                  .build()
                                  .encode();
        var second = MessageBuilder.builder(Command.MESSAGE)
                                   .header(Header.DESTINATION, "/topic/2")
                                   .header(Header.MESSAGE_ID, "2")
                                   .body("two")
                                   .build()
                                   .encode();
        assertThat(buffer.append(first.getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(buffer.message().body()).isEqualTo("one");
        assertThat(buffer.append(second.getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(buffer.message().body()).isEqualTo("two");
    }

    @Test
    void shouldRoundTripStompConnectCommand() {
        var message = MessageBuilder.builder(Command.STOMP)
                                    .header(Header.ACCEPT_VERSION, "1.2")
                                    .header(Header.HOST, "example.org")
                                    .build();
        var encoded = message.encode();
        var decoded = Message.readMessage(encoded.substring(0, encoded.length() - 1));
        assertThat(decoded.command()).isEqualTo(Command.STOMP);
        assertThat(decoded.headers().get(Header.HOST)).contains("example.org");
    }

    @Test
    void shouldRoundTripTransactionCommands() {
        var begin = MessageBuilder.builder(Command.BEGIN)
                                  .header(Header.TRANSACTION, "tx-1")
                                  .build();
        var commit = MessageBuilder.builder(Command.COMMIT)
                                   .header(Header.TRANSACTION, "tx-1")
                                   .build();
        var abort = MessageBuilder.builder(Command.ABORT)
                                  .header(Header.TRANSACTION, "tx-1")
                                  .build();
        assertThat(Message.readMessage(begin.encode().substring(0, begin.encode().length() - 1)).command())
                                                                                                           .isEqualTo(Command.BEGIN);
        assertThat(Message.readMessage(commit.encode().substring(0, commit.encode().length() - 1)).command())
                                                                                                             .isEqualTo(Command.COMMIT);
        assertThat(Message.readMessage(abort.encode().substring(0, abort.encode().length() - 1)).command())
                                                                                                           .isEqualTo(Command.ABORT);
    }
}
