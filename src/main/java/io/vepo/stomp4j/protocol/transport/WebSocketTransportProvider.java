package io.vepo.stomp4j.protocol.transport;

import java.net.URI;

import io.vepo.stomp4j.protocol.StompListener;
import io.vepo.stomp4j.protocol.Transport;
import io.vepo.stomp4j.protocol.TransportProvider;

public class WebSocketTransportProvider implements TransportProvider {
    @Override
    public String protocol() {
        return "ws";
    }

    @Override
    public Transport getTransport(URI uri, StompListener listener) {
        return new WebSocketTransport(uri, listener);
    }

}
