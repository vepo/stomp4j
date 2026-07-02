package dev.vepo.stomp4j.integration.client;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.StompDelivery;

public final class InboundAckPolicy {

    public static void afterInvocation(AckMode ackMode,
                                       StompDelivery delivery,
                                       Acknowledgment acknowledgment,
                                       boolean threw) {
        if (delivery.acknowledged()) {
            return;
        }
        if (ackMode.requiresManualAcknowledgement()) {
            if (threw) {
                acknowledgment.nack();
            }
            return;
        }
        acknowledgment.acknowledge();
    }

    private InboundAckPolicy() {}
}
