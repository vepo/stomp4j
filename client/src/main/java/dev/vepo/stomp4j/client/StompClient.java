package dev.vepo.stomp4j.client;

import java.util.Set;
import java.util.function.Consumer;

import dev.vepo.stomp4j.client.internal.StompClientImpl;
import dev.vepo.stomp4j.client.protocol.Stomp;
import dev.vepo.stomp4j.commons.TransportType;

public interface StompClient extends AutoCloseable {

    public static StompClient create(String url) {
        return new StompClientImpl(url, null, null, Stomp.ALL_VERSIONS);
    }

    public static StompClient create(String url, TransportType transportType) {
        return new StompClientImpl(url, null, transportType, Stomp.ALL_VERSIONS);
    }

    public static StompClient create(String url, UserCredential credentials) {
        return new StompClientImpl(url, credentials, null, Stomp.ALL_VERSIONS);
    }

    public static StompClient create(String url, TransportType transportType, UserCredential credentials) {
        return new StompClientImpl(url, credentials, transportType, Stomp.ALL_VERSIONS);
    }

    public static StompClient create(String url, UserCredential credentials, Set<Stomp> protocols) {
        return new StompClientImpl(url, credentials, null, protocols);
    }

    public static StompClient create(String url, UserCredential credentials, TransportType transportType, Set<Stomp> protocols) {
        return new StompClientImpl(url, credentials, transportType, protocols);
    }

    StompClient connect();

    Subscription subscribe(String topic);

    void join();

    Subscription subscribe(String topic, Consumer<String> consumer);

    void sendPlain(String destination, String content, String contentType);

    StompClient unsubscribe(Subscription subscription);

    StompClient unsubscribe(String topic);

    void close();
}
