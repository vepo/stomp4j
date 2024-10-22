package dev.vepo.stomp4j.protocol;

public interface TransportListener {
    void onConnected(Transport transport);

    void onMessage(Message message);

    void onError(Message message);
}
