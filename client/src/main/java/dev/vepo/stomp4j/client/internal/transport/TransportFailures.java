package dev.vepo.stomp4j.client.internal.transport;

import dev.vepo.stomp4j.client.exceptions.StompException;

final class TransportFailures {

    static StompException connectFailed(String target, Exception cause) {
        return new StompException("Failed to connect to %s".formatted(target), cause);
    }

    static StompException notConnected() {
        return new StompException("Transport is not connected");
    }

    static StompException sendFailed(Exception cause) {
        return new StompException("Failed to send STOMP frame", cause);
    }

    private TransportFailures() {}
}
