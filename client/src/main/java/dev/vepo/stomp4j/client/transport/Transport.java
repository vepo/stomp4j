package dev.vepo.stomp4j.client.transport;

import java.io.Closeable;

import dev.vepo.stomp4j.commons.protocol.Message;

public interface Transport extends Closeable {

    void send(Message message);

    void connect();

    String host();

    @Override
    void close();

    long silentTime();
}
