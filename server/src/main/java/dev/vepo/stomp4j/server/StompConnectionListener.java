package dev.vepo.stomp4j.server;

public interface StompConnectionListener {

    default void onConnected(StompSession session) {}

    default void onDisconnected(StompSession session) {}
}
