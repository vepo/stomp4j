package dev.vepo.stomp4j.client;

public interface StompTransaction extends AutoCloseable {

    void abort();

    @Override
    void close();

    void commit();

    String id();

    void send(String destination, String body, SendOptions options);
}
