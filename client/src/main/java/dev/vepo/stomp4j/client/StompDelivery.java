package dev.vepo.stomp4j.client;

import dev.vepo.stomp4j.commons.protocol.Headers;

public interface StompDelivery {

    void ack();

    boolean acknowledged();

    void autoAcknowledgeIfNeeded();

    String body();

    String destination();

    Headers headers();

    String messageId();

    void nack();

    Subscription subscription();
}
