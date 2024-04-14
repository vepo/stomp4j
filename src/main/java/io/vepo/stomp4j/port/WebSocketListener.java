package io.vepo.stomp4j.port;

public interface WebSocketListener {
    void connectionOpened();

    void messageReceived(String content);

    void error(Throwable error);
}
