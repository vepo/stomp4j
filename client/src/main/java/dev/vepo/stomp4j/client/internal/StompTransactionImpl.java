package dev.vepo.stomp4j.client.internal;

import java.util.Objects;

import dev.vepo.stomp4j.client.SendOptions;
import dev.vepo.stomp4j.client.StompTransaction;
import dev.vepo.stomp4j.client.exceptions.StompException;

final class StompTransactionImpl implements StompTransaction {

    private final StompClientImpl client;
    private final String transactionId;
    private boolean finished;

    StompTransactionImpl(StompClientImpl client, String transactionId) {
        this.client = client;
        this.transactionId = transactionId;
    }

    @Override
    public void abort() {
        ensureActive();
        client.abortTransaction(transactionId);
        finished = true;
    }

    @Override
    public void close() {
        if (!finished) {
            abort();
        }
    }

    @Override
    public void commit() {
        ensureActive();
        client.commitTransaction(transactionId);
        finished = true;
    }

    @Override
    public String id() {
        return transactionId;
    }

    @Override
    public void send(String destination, String body, SendOptions options) {
        ensureActive();
        client.sendInTransaction(transactionId, destination, body, options);
    }

    private void ensureActive() {
        if (finished) {
            throw new StompException("Transaction %s is no longer active".formatted(transactionId));
        }
    }

    @Override
    public String toString() {
        return "StompTransaction[id=%s, finished=%s]".formatted(transactionId, finished);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (Objects.isNull(obj) || obj.getClass() != getClass()) {
            return false;
        }
        return Objects.equals(transactionId, ((StompTransactionImpl) obj).transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }
}
