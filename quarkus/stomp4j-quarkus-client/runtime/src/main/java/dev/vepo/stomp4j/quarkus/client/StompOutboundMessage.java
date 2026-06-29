package dev.vepo.stomp4j.quarkus.client;

import java.time.Duration;

import dev.vepo.stomp4j.client.SendOptions;

public record StompOutboundMessage(
                                   String destination,
                                   String body,
                                   SendOptions options) {

    public static StompOutboundMessage of(String destination, String body) {
        return new StompOutboundMessage(destination, body, SendOptions.plainText());
    }

    public StompOutboundMessage withReceipt(Duration timeout) {
        return new StompOutboundMessage(
                                        destination,
                                        body,
                                        SendOptions.builder().receipt(true).receiptTimeout(timeout).build());
    }
}
