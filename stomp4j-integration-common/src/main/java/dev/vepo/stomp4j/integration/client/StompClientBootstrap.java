package dev.vepo.stomp4j.integration.client;

import dev.vepo.stomp4j.client.StompClient;

public final class StompClientBootstrap {

    public static StompClient create(StompClientBootstrapOptions options) {
        var url = options.url();
        var transportType = options.transportType();
        var credentials = options.credentials();
        var sslContext = options.sslContext();

        if (transportType.isPresent() && credentials.isPresent()) {
            return StompClient.create(url, transportType.get(), credentials.get());
        }
        if (transportType.isPresent()) {
            return StompClient.create(url, transportType.get());
        }
        if (sslContext.isPresent()) {
            return StompClient.create(url, credentials.orElse(null), sslContext.get());
        }
        if (credentials.isPresent()) {
            return StompClient.create(url, credentials.get());
        }
        return StompClient.create(url);
    }

    private StompClientBootstrap() {}
}
