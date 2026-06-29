package dev.vepo.stomp4j.server.ssl;

import java.util.Optional;

import javax.net.ssl.SSLContext;

public record SslSettings(
                          SSLContext sslContext,
                          Optional<KeyStoreLocation> keyStoreLocation) {
    public record KeyStoreLocation(String path, String password) {}
}
