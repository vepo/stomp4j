package dev.vepo.stomp4j.integration.client.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.integration.client.StompClientBootstrap;
import dev.vepo.stomp4j.integration.client.StompClientBootstrapOptions;

class StompClientBootstrapTest {

    @Test
    void shouldCreateClientFromUrlOnly() {
        var client = StompClientBootstrap.create(new StompClientBootstrapOptions("stomp://localhost:61613",
                                                                                 Optional.empty(),
                                                                                 Optional.empty(),
                                                                                 Optional.empty()));
        assertThat(client).isInstanceOf(StompClient.class);
    }

    @Test
    void shouldCreateClientWithSslContext() throws Exception {
        var client = StompClientBootstrap.create(new StompClientBootstrapOptions("stomps://localhost:61613",
                                                                                 Optional.empty(),
                                                                                 Optional.empty(),
                                                                                 Optional.of(SSLContext.getDefault())));
        assertThat(client).isInstanceOf(StompClient.class);
    }

    @Test
    void shouldCreateClientWithTransportTypeAndCredentials() {
        var client = StompClientBootstrap.create(new StompClientBootstrapOptions("stomp://localhost:61613",
                                                                                 Optional.of(TransportType.TCP),
                                                                                 Optional.of(new UserCredential("u", "p")),
                                                                                 Optional.empty()));
        assertThat(client).isInstanceOf(StompClient.class);
    }
}
