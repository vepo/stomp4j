package io.vepo.stomp4j.protocol;

import java.net.URI;

public interface TransportProvider {

    String protocol();

    Transport getTransport(URI uri, StompListener listener);
}
