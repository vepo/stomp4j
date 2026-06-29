package dev.vepo.stomp4j.client;

import dev.vepo.stomp4j.commons.protocol.Headers;

public interface StompDelivery {

    String body();

    Headers headers();

    String messageId();

    String destination();

    Subscription subscription();

    void ack();

    void nack();

    boolean acknowledged();

    void autoAcknowledgeIfNeeded();
}
