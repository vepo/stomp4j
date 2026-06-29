package dev.vepo.stomp4j.samples.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.vepo.stomp4j.server.SubscriptionHandler;

@Component
public class ChatSubscriptionHandler implements SubscriptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatSubscriptionHandler.class);

    @Override
    public boolean accept(String topic) {
        var allowed = topic.startsWith("/topic/chat/") || topic.startsWith("/app/chat/");
        if (!allowed) {
            logger.warn("Rejected subscription to {}", topic);
        }
        return allowed;
    }
}
