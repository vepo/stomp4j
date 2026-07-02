package dev.vepo.stomp4j.client.internal;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Knowing:</b> Pending STOMP receipt ids awaiting broker {@code RECEIPT}
 * or {@code ERROR} frames.</li>
 * <li><b>Doing:</b> Register futures for outbound receipts and complete or fail
 * them when matching inbound frames arrive.</li>
 * </ul>
 * <p>
 * <b>Not responsible for:</b> Sending frames, protocol version selection.
 * </p>
 */
final class ReceiptTracker {

    private final Map<String, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();

    void completeReceipt(Message message) {
        message.headers()
               .get(Header.RECEIPT_ID)
               .ifPresent(receiptId -> {
                   var completion = pending.remove(receiptId);
                   if (Objects.nonNull(completion)) {
                       completion.complete(null);
                   }
               });
    }

    void failAllOnClose() {
        pending.values()
               .forEach(future -> future.completeExceptionally(new StompException("Client closed")));
        pending.clear();
    }

    boolean failFromError(Message message) {
        var receiptId = message.headers().get(Header.RECEIPT_ID);
        if (receiptId.isPresent()) {
            return failPending(receiptId.get(), message);
        }
        var receiptHeader = message.headers().get(Header.RECEIPT);
        if (receiptHeader.isPresent()) {
            return failPending(receiptHeader.get(), message);
        }
        return false;
    }

    private boolean failPending(String receiptId, Message message) {
        var completion = pending.remove(receiptId);
        if (Objects.nonNull(completion)) {
            var errorMessage = message.headers().get(Header.MESSAGE).orElse(message.body());
            completion.completeExceptionally(new StompException(errorMessage));
            return true;
        }
        return false;
    }

    CompletableFuture<Void> register(String receiptId) {
        var completion = new CompletableFuture<Void>();
        pending.put(receiptId, completion);
        return completion;
    }

    void remove(String receiptId, CompletableFuture<Void> completion) {
        pending.remove(receiptId, completion);
    }
}
