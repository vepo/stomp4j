package dev.vepo.stomp4j.server;

public interface SubscriptionHandler {

    boolean accept(String topic);

    default void onSubscribed(StompSession session, String topic) {}

    default void onUnsubscribed(StompSession session, String topic) {}
}
