package dev.vepo.stomp4j.commons.tests.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.HeaderCodec;
import dev.vepo.stomp4j.commons.protocol.StompFrameException;

class HeaderCodecTest {

    @Test
    void shouldEscapeHeaderValueForStomp12Frames() {
        assertThat(HeaderCodec.escape("a:b")).isEqualTo("a\\cb");
        assertThat(HeaderCodec.escape("line\nbreak")).isEqualTo("line\\nbreak");
        assertThat(HeaderCodec.escape("back\\slash")).isEqualTo("back\\\\slash");
    }

    @Test
    void shouldNotEscapeConnectHeaders() {
        assertThat(HeaderCodec.shouldEscape(Command.CONNECT)).isFalse();
        assertThat(HeaderCodec.shouldEscape(Command.CONNECTED)).isFalse();
        assertThat(HeaderCodec.shouldEscape(Command.STOMP)).isFalse();
        assertThat(HeaderCodec.shouldEscape(Command.SEND)).isTrue();
    }

    @Test
    void shouldRejectUndefinedEscapeSequence() {
        assertThatThrownBy(() -> HeaderCodec.unescape("value\\t"))
                                                                  .isInstanceOf(StompFrameException.class)
                                                                  .hasMessageContaining("Undefined escape sequence");
    }

    @Test
    void shouldRoundTripEscapedHeaderValue() {
        var original = "value:with\nspecial\\chars";
        assertThat(HeaderCodec.unescape(HeaderCodec.escape(original))).isEqualTo(original);
    }
}
