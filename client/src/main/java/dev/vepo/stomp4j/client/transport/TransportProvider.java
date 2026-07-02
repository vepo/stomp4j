package dev.vepo.stomp4j.client.transport;

import java.net.URI;

import javax.net.ssl.SSLContext;

public interface TransportProvider {

    Transport getTransport(URI uri, TransportListener listener);

    default Transport getTransport(URI uri, TransportListener listener, SSLContext sslContext) {
        return getTransport(uri, listener);
    }

    String protocol();
}
