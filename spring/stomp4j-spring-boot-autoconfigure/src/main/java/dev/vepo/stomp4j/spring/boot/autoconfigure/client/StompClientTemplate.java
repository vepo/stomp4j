package dev.vepo.stomp4j.spring.boot.autoconfigure.client;

import dev.vepo.stomp4j.client.SendOptions;
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.StompReceipt;

public class StompClientTemplate {

    private final StompClientLifecycle clientLifecycle;
    private final StompClientProperties properties;

    public StompClientTemplate(StompClientLifecycle clientLifecycle, StompClientProperties properties) {
        this.clientLifecycle = clientLifecycle;
        this.properties = properties;
    }

    public StompClient client() {
        return clientLifecycle.client();
    }

    public StompReceipt send(String destination, String body, SendOptions options) {
        return client().send(destination, body, options);
    }

    public void sendPlain(String destination, String body) {
        sendPlain(destination, body, "text/plain");
    }

    public void sendPlain(String destination, String body, String contentType) {
        client().sendPlain(destination, body, contentType);
    }

    public StompReceipt sendWithReceipt(String destination, String body) {
        return send(destination, body, SendOptions.builder()
                                                  .receipt(true)
                                                  .receiptTimeout(properties.getReceiptTimeout())
                                                  .build());
    }
}
