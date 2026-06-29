package dev.vepo.stomp4j.spring.boot.autoconfigure.server;

import java.util.Objects;
import java.util.function.BiConsumer;

import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.AcknowledgedOutboundChannel;
import dev.vepo.stomp4j.server.StompServer;
import dev.vepo.stomp4j.server.StompSession;
import dev.vepo.stomp4j.server.SubscriberAckListener;

public class StompOutboundTemplate {

    private final StompServer server;

    public StompOutboundTemplate(StompServer server) {
        this.server = server;
    }

    public void send(Message message) {
        server.outboundChannel().send(message);
    }

    public void send(Message message, SubscriberAckCallbacks callbacks) {
        Objects.requireNonNull(callbacks, "callbacks cannot be null");
        acknowledgedChannel().send(message, callbacks.toListener());
    }

    public AcknowledgedOutboundChannel acknowledgedChannel() {
        return server.acknowledgedOutboundChannel();
    }

    public static final class SubscriberAckCallbacks {
        private BiConsumer<String, StompSession> onSubscriberAck;
        private BiConsumer<String, StompSession> onSubscriberNack;

        public static Builder builder() {
            return new Builder();
        }

        public SubscriberAckListener toListener() {
            return new SubscriberAckListener() {
                @Override
                public void onAck(String messageId, StompSession session) {
                    if (onSubscriberAck != null) {
                        onSubscriberAck.accept(messageId, session);
                    }
                }

                @Override
                public void onNack(String messageId, StompSession session) {
                    if (onSubscriberNack != null) {
                        onSubscriberNack.accept(messageId, session);
                    }
                }
            };
        }

        public static final class Builder {
            private final SubscriberAckCallbacks callbacks = new SubscriberAckCallbacks();

            public Builder onSubscriberAck(BiConsumer<String, StompSession> onSubscriberAck) {
                callbacks.onSubscriberAck = onSubscriberAck;
                return this;
            }

            public Builder onSubscriberNack(BiConsumer<String, StompSession> onSubscriberNack) {
                callbacks.onSubscriberNack = onSubscriberNack;
                return this;
            }

            public SubscriberAckCallbacks build() {
                return callbacks;
            }
        }
    }
}
