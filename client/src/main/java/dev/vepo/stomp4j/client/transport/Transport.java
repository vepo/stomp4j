package dev.vepo.stomp4j.client.transport;

import java.io.Closeable;

import dev.vepo.stomp4j.commons.protocol.Message;

public interface Transport extends Closeable {

    @Override
    void close();

    void connect();

    String host();

    /**
     * Milliseconds since the last outbound wire activity (including heart-beats).
     */
    long outboundSilentTime();

    void send(Message message);

    /**
     * Milliseconds since the last inbound wire activity (including heart-beats).
     */
    long silentTime();
}
