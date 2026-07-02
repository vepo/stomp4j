package dev.vepo.stomp4j.client.internal.transport;

import java.net.URI;

import javax.net.ssl.SSLContext;

import dev.vepo.stomp4j.client.transport.TransportListener;

/**
 * TLS WebSocket transport — delegates to {@link WebSocketTransport} with an
 * {@link SSLContext}.
 */
public class SecureWebSocketTransport extends WebSocketTransport {

    private static SSLContext defaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load default SSL context", ex);
        }
    }

    public SecureWebSocketTransport(URI uri, TransportListener listener) {
        super(uri, listener, defaultSslContext());
    }

    public SecureWebSocketTransport(URI uri, TransportListener listener, SSLContext sslContext) {
        super(uri, listener, sslContext);
    }
}
