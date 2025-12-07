package dev.vepo.stomp4j.client.transport;

import java.net.URI;

public interface TransportProvider {

    String protocol();

    Transport getTransport(URI uri, TransportListener listener);
}
