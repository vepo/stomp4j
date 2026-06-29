package dev.vepo.stomp4j.client.transport;

import java.io.Closeable;

import dev.vepo.stomp4j.commons.protocol.Message;

public interface Transport extends Closeable {

    @Override
    void close();

    void connect();

    String host();

    void send(Message message);

    long silentTime();
}
