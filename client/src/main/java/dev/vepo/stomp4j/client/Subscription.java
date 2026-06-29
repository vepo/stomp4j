package dev.vepo.stomp4j.client;

import java.util.List;

public interface Subscription {
    AckMode ackMode();

    boolean autoAckAfterDelivery();

    boolean hasData();

    int id();

    List<String> poll();

    boolean requiresManualAcknowledgement();

    String topic();
}