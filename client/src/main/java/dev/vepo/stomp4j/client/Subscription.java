package dev.vepo.stomp4j.client;

import java.util.List;

public interface Subscription {
    String topic();

    int id();

    boolean hasData();

    List<String> poll();
}