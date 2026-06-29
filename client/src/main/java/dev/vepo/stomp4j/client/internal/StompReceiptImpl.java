package dev.vepo.stomp4j.client.internal;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import dev.vepo.stomp4j.client.StompReceipt;

final class StompReceiptImpl implements StompReceipt {

    private final String receiptId;
    private final CompletableFuture<Void> completion;

    StompReceiptImpl(String receiptId, CompletableFuture<Void> completion) {
        this.receiptId = receiptId;
        this.completion = completion;
    }

    @Override
    public String receiptId() {
        return receiptId;
    }

    @Override
    public CompletableFuture<Void> completion() {
        return completion;
    }

    @Override
    public String toString() {
        return "StompReceipt[receiptId=%s]".formatted(receiptId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiptId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (Objects.isNull(obj) || obj.getClass() != getClass()) {
            return false;
        }
        return Objects.equals(receiptId, ((StompReceiptImpl) obj).receiptId);
    }
}
