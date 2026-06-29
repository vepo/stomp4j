package dev.vepo.stomp4j.client;

import java.util.Set;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;

import dev.vepo.stomp4j.client.internal.StompClientImpl;
import dev.vepo.stomp4j.client.protocol.Stomp;
import dev.vepo.stomp4j.commons.TransportType;

public interface StompClient extends AutoCloseable {

    public static StompClient create(String url) {
        return new StompClientImpl(url, null, null, Stomp.ALL_VERSIONS, null);
    }

    public static StompClient create(String url, SSLContext sslContext) {
        return new StompClientImpl(url, null, null, Stomp.ALL_VERSIONS, sslContext);
    }

    public static StompClient create(String url, TransportType transportType) {
        return new StompClientImpl(url, null, transportType, Stomp.ALL_VERSIONS, null);
    }

    public static StompClient create(String url, TransportType transportType, UserCredential credentials) {
        return new StompClientImpl(url, credentials, transportType, Stomp.ALL_VERSIONS, null);
    }

    public static StompClient create(String url, UserCredential credentials) {
        return new StompClientImpl(url, credentials, null, Stomp.ALL_VERSIONS, null);
    }

    public static StompClient create(String url, UserCredential credentials, Set<Stomp> protocols) {
        return new StompClientImpl(url, credentials, null, protocols, null);
    }

    public static StompClient create(String url, UserCredential credentials, SSLContext sslContext) {
        return new StompClientImpl(url, credentials, null, Stomp.ALL_VERSIONS, sslContext);
    }

    public static StompClient create(String url, UserCredential credentials, TransportType transportType, Set<Stomp> protocols) {
        return new StompClientImpl(url, credentials, transportType, protocols, null);
    }

    void close();

    StompClient connect();

    void join();

    StompReceipt send(String destination, String body, SendOptions options);

    void sendPlain(String destination, String content, String contentType);

    Subscription subscribe(String topic);

    Subscription subscribe(String topic, AckMode ackMode, Consumer<StompDelivery> consumer);

    Subscription subscribe(String topic, Consumer<String> consumer);

    StompClient unsubscribe(String topic);

    StompClient unsubscribe(Subscription subscription);
}
