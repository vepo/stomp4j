package dev.vepo.stomp4j.client;

public interface StompTransaction extends AutoCloseable {

    String id();

    void send(String destination, String body, SendOptions options);

    void commit();

    void abort();

    @Override
    void close();
}
