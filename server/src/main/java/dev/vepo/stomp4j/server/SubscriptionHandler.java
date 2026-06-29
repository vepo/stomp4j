package dev.vepo.stomp4j.server;

public interface SubscriptionHandler {

    boolean accept(String topic);

}
