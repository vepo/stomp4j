package dev.vepo.stomp4j.client.tests;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.commons.TransportType;

class StompClientTest {

    @Test
    void invalidURLTest() {
        assertThatThrownBy(() -> StompClient.create("xxxx")).isInstanceOf(IllegalArgumentException.class)
                                                            .hasMessage("No transport found for protocol null");
        assertThatThrownBy(() -> StompClient.create("invalid url", TransportType.WEB_SOCKET)).isInstanceOf(IllegalArgumentException.class)
                                                                                             .hasMessage("Invalid URL: invalid url");
    }

}
