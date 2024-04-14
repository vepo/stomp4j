package io.vepo.stomp4j.protocol;

public interface StompEventListener {

    void connected();

    void message(Message message);

    void error(Message message);
    
}
