package dev.vepo.stomp4j.client;

import java.util.List;

public interface Subscription {
    String topic();

    int id();

    AckMode ackMode();

    boolean requiresManualAcknowledgement();

    boolean autoAckAfterDelivery();

    boolean hasData();

    List<String> poll();
}