package io.vepo.stomp4j.protocol;

import java.io.Closeable;
import java.net.URI;
import java.util.ServiceLoader;

public interface Transport extends Closeable {

    @SuppressWarnings("resource")
    static Transport create(URI uri, TransportListener listener) {
        return ServiceLoader.load(TransportProvider.class)
                            .stream()
                            .map(ServiceLoader.Provider::get)
                            .filter(transport -> transport.protocol().equals(uri.getScheme()))
                            .findFirst()
                            .map(transportProvider -> transportProvider.getTransport(uri, listener))
                            .orElseThrow(() -> new IllegalArgumentException("No transport found for protocol " + uri.getScheme()));
    }

    void send(String content);

    void connect();

    String host();

    @Override
    void close();

    long silentTime();
}
