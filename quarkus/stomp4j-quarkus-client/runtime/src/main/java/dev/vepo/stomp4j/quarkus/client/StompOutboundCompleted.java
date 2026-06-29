package dev.vepo.stomp4j.quarkus.client;

public record StompOutboundCompleted(
                                     String destination,
                                     String receiptId,
                                     Throwable failure) {}
