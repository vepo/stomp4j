package io.vepo.stomp4j.protocol;

public interface StompListener {
    void connected(Transport transport);

    void message(Message message);

    void error(Message message);
}
