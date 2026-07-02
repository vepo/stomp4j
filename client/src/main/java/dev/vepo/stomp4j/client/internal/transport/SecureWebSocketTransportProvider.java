package dev.vepo.stomp4j.client.internal.transport;

import java.net.URI;
import java.util.Objects;

import javax.net.ssl.SSLContext;

import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.client.transport.TransportProvider;

public class SecureWebSocketTransportProvider implements TransportProvider {

    @Override
    public Transport getTransport(URI uri, TransportListener listener) {
        return getTransport(uri, listener, null);
    }

    @Override
    public Transport getTransport(URI uri, TransportListener listener, SSLContext sslContext) {
        return Objects.isNull(sslContext)
                                          ? new SecureWebSocketTransport(uri, listener)
                                          : new SecureWebSocketTransport(uri, listener, sslContext);
    }

    @Override
    public String protocol() {
        return "wss";
    }
}
