package dev.vepo.stomp4j.tests;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.StompClient;
import dev.vepo.stomp4j.TransportType;

class StompClientTest {

    @Test
    void invalidURLTest() {
        assertThatThrownBy(() -> new StompClient("xxxx")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No transport found for protocol null");
        assertThatThrownBy(() -> new StompClient("invalid url", TransportType.WEB_SOCKET)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid URL: invalid url");
    }

}
