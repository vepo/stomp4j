package dev.vepo.stomp4j.client.transport;

import java.net.URI;

public interface TransportProvider {

    Transport getTransport(URI uri, TransportListener listener);

    String protocol();
}
