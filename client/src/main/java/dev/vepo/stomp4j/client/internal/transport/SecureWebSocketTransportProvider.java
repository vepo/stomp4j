package dev.vepo.stomp4j.client.internal.transport;

import java.net.URI;

import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.client.transport.TransportProvider;

public class SecureWebSocketTransportProvider implements TransportProvider {

    @Override
    public String protocol() {
        return "wss";
    }

    @Override
    public Transport getTransport(URI uri, TransportListener listener) {
        return new SecureWebSocketTransport(uri, listener);
    }
}
