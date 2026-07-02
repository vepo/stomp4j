package dev.vepo.stomp4j.client;

import java.time.Duration;
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

    StompTransaction beginTransaction();

    void close();

    void close(Duration gracePeriod);

    /**
     * Opens the transport and completes STOMP {@code CONNECT} → {@code CONNECTED}
     * negotiation.
     *
     * <p>
     * On failure, the client is closed automatically — transport, heartbeat
     * executor, and related resources are released before the exception is thrown.
     * A failed client must not be reused; create a new instance or use
     * try-with-resources so {@link #close()} runs on exit.
     *
     * @return this client for chaining
     * @throws dev.vepo.stomp4j.client.exceptions.StompException when connect or
     *                                                           negotiation fails
     */
    StompClient connect();

    void join();

    /**
     * Sends a message to {@code destination}. After {@link #connect()}, this method
     * may be called from multiple application threads; TCP/TLS transports serialise
     * outbound frames internally while inbound data is delivered on the transport
     * I/O thread.
     */
    StompReceipt send(String destination, String body, SendOptions options);

    void sendPlain(String destination, String content, String contentType);

    Subscription subscribe(String topic);

    Subscription subscribe(String topic, AckMode ackMode, Consumer<StompDelivery> consumer);

    Subscription subscribe(String topic, Consumer<String> consumer);

    Subscription subscribe(String topic, SubscribeOptions options);

    Subscription subscribe(String topic, SubscribeOptions options, Consumer<StompDelivery> consumer);

    StompClient unsubscribe(String topic);

    StompClient unsubscribe(Subscription subscription);
}
