package dev.vepo.stomp4j.server.session;

import java.util.ArrayList;
import java.util.List;

final class TransactionState {

    private final List<Runnable> onCommit;

    TransactionState() {
        this.onCommit = new ArrayList<>();
    }

    void commit() {
        onCommit.forEach(Runnable::run);
    }

    void defer(Runnable action) {
        onCommit.add(action);
    }
}
