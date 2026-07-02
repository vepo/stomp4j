package dev.vepo.stomp4j.spring.boot.autoconfigure.client;

import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLContext;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.integration.client.StompClientBootstrap;
import dev.vepo.stomp4j.integration.client.StompClientBootstrapOptions;

public class StompClientFactory {

    private final StompClientProperties properties;
    private final SSLContext sslContext;

    public StompClientFactory(StompClientProperties properties, SSLContext sslContext) {
        this.properties = properties;
        this.sslContext = sslContext;
    }

    private UserCredential buildCredentials() {
        if (Objects.isNull(properties.getUsername())) {
            return null;
        }
        return new UserCredential(properties.getUsername(), properties.getPassword());
    }

    public StompClient create() {
        return StompClientBootstrap.create(new StompClientBootstrapOptions(properties.getUrl(),
                                                                           Optional.ofNullable(properties.getTransportType()),
                                                                           Optional.ofNullable(buildCredentials()),
                                                                           Optional.ofNullable(sslContext)));
    }

    public StompClient create(String url, UserCredential credentials) {
        return StompClient.create(url, credentials);
    }
}
