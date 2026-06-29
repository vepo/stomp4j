package dev.vepo.stomp4j.quarkus.client;

import java.util.Optional;

import javax.net.ssl.SSLContext;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class StompClientFactory {

    private final StompClientConfig config;
    private final Optional<SSLContext> sslContext;

    @Inject
    public StompClientFactory(StompClientConfig config, Instance<SSLContext> sslContext) {
        this.config = config;
        this.sslContext = sslContext.isResolvable() ? Optional.of(sslContext.get()) : Optional.empty();
    }

    private Optional<UserCredential> buildCredentials() {
        if (config.username().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UserCredential(config.username().get(), config.password().orElse(null)));
    }

    public StompClient create() {
        var credentials = buildCredentials();
        if (config.transportType().isPresent() && credentials.isPresent()) {
            return StompClient.create(config.url(), config.transportType().get(), credentials.get());
        }
        if (config.transportType().isPresent()) {
            return StompClient.create(config.url(), config.transportType().get());
        }
        if (sslContext.isPresent() && credentials.isPresent()) {
            return StompClient.create(config.url(), credentials.get(), sslContext.get());
        }
        if (credentials.isPresent()) {
            return StompClient.create(config.url(), credentials.get());
        }
        return StompClient.create(config.url());
    }
}
