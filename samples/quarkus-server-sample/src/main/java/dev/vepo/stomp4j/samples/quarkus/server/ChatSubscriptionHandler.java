package dev.vepo.stomp4j.samples.quarkus.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.server.SubscriptionHandler;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
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
