package dev.vepo.stomp4j.client.protocol;

import java.util.Map;
import java.util.Optional;

public record SendParameters(Map<String, String> customHeaders,
                             Optional<String> receiptId,
                             Optional<String> transactionId) {

    public static SendParameters plain() {
        return new SendParameters(Map.of(), Optional.empty(), Optional.empty());
    }

    public static SendParameters withReceipt(Optional<String> receiptId) {
        return new SendParameters(Map.of(), receiptId, Optional.empty());
    }

    public static SendParameters withTransaction(Optional<String> transactionId, Map<String, String> customHeaders) {
        return new SendParameters(customHeaders, Optional.empty(), transactionId);
    }

    public static SendParameters of(Map<String, String> customHeaders,
                                    Optional<String> receiptId,
                                    Optional<String> transactionId) {
        return new SendParameters(customHeaders, receiptId, transactionId);
    }
}
