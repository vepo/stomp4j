package dev.vepo.stomp4j.spring.boot.autoconfigure.client;

import java.util.Objects;

import javax.net.ssl.SSLContext;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;

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
        var credentials = buildCredentials();
        if (Objects.nonNull(properties.getTransportType()) && Objects.nonNull(credentials)) {
            return StompClient.create(properties.getUrl(), properties.getTransportType(), credentials);
        }
        if (Objects.nonNull(properties.getTransportType())) {
            return StompClient.create(properties.getUrl(), properties.getTransportType());
        }
        if (Objects.nonNull(sslContext)) {
            return StompClient.create(properties.getUrl(), credentials, sslContext);
        }
        if (Objects.nonNull(credentials)) {
            return StompClient.create(properties.getUrl(), credentials);
        }
        return StompClient.create(properties.getUrl());
    }

    public StompClient create(String url, UserCredential credentials) {
        return StompClient.create(url, credentials);
    }
}
