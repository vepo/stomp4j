package dev.vepo.stomp4j.client.internal.transport;

import java.net.URI;
import java.util.ServiceLoader;

import javax.net.ssl.SSLContext;

import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.client.transport.TransportProvider;

public abstract class TransportFactory {

    public static Transport create(URI uri, TransportListener listener) {
        return create(uri, listener, null);
    }

    public static Transport create(URI uri, TransportListener listener, SSLContext sslContext) {
        return ServiceLoader.load(TransportProvider.class)
                            .stream()
                            .map(ServiceLoader.Provider::get)
                            .filter(transport -> transport.protocol().equals(uri.getScheme()))
                            .findFirst()
                            .map(transportProvider -> transportProvider.getTransport(uri, listener, sslContext))
                            .orElseThrow(() -> new IllegalArgumentException("No transport found for protocol " + uri.getScheme()));
    }

    private TransportFactory() {
        throw new IllegalStateException("Utility class!");
    }
}
