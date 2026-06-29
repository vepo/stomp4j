package dev.vepo.stomp4j.client;

import java.util.concurrent.CompletableFuture;

public interface StompReceipt {

    CompletableFuture<Void> completion();

    String receiptId();
}
