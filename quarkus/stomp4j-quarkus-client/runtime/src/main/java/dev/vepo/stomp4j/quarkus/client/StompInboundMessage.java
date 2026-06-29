package dev.vepo.stomp4j.quarkus.client;

import dev.vepo.stomp4j.client.StompDelivery;
import dev.vepo.stomp4j.commons.protocol.Headers;

public record StompInboundMessage(
                                  String destination,
                                  StompDelivery delivery,
                                  Headers headers) {

    public StompInboundMessage(String destination, StompDelivery delivery) {
        this(destination, delivery, delivery.headers());
    }
}
