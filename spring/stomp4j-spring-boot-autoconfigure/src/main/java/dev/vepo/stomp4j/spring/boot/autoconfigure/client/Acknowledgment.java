package dev.vepo.stomp4j.spring.boot.autoconfigure.client;

import dev.vepo.stomp4j.client.StompDelivery;

public interface Acknowledgment {

    static Acknowledgment of(StompDelivery delivery) {
        return new Acknowledgment() {
            @Override
            public void acknowledge() {
                if (delivery.subscription().requiresManualAcknowledgement()) {
                    delivery.ack();
                } else {
                    delivery.autoAcknowledgeIfNeeded();
                }
            }

            @Override
            public void nack() {
                delivery.nack();
            }
        };
    }

    void acknowledge();

    void nack();
}
