package dev.vepo.stomp4j.samples.client;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.StompDelivery;
import dev.vepo.stomp4j.integration.client.Acknowledgment;
import dev.vepo.stomp4j.spring.boot.autoconfigure.client.StompClientTemplate;
import dev.vepo.stomp4j.spring.boot.autoconfigure.client.StompListener;

@RestController
public class PublishController {

    private final StompClientTemplate stompClientTemplate;
    private final AtomicReference<String> lastInbound = new AtomicReference<>();

    public PublishController(StompClientTemplate stompClientTemplate) {
        this.stompClientTemplate = stompClientTemplate;
    }

    public String lastInbound() {
        return lastInbound.get();
    }

    @StompListener(destination = "/queue/demo.in", ackMode = AckMode.CLIENT_INDIVIDUAL)
    public void onInbound(StompDelivery delivery, Acknowledgment acknowledgment) {
        lastInbound.set(delivery.body());
        acknowledgment.acknowledge();
    }

    @PostMapping("/publish")
    public String publish(@RequestBody String body) throws Exception {
        var receipt = stompClientTemplate.sendWithReceipt("/queue/demo.out", body);
        receipt.completion().get();
        return "sent";
    }
}
