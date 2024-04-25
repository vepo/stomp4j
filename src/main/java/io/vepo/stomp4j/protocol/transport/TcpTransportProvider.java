package io.vepo.stomp4j.protocol.transport;

import java.net.URI;

import io.vepo.stomp4j.protocol.TransportListener;
import io.vepo.stomp4j.protocol.Transport;
import io.vepo.stomp4j.protocol.TransportProvider;

public class TcpTransportProvider implements TransportProvider {
    @Override
    public String protocol() {
        return "stomp";
    }

    @Override
    public Transport getTransport(URI uri, TransportListener listener) {
        return new TcpTransport(uri, listener);
    }

}
