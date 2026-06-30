package dev.vepo.stomp4j.client.internal;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.vepo.stomp4j.client.StompDelivery;
import dev.vepo.stomp4j.client.Subscription;
import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.client.protocol.AcknowledgementIds;
import dev.vepo.stomp4j.client.protocol.Stomp;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.commons.protocol.Headers;
import dev.vepo.stomp4j.commons.protocol.Message;

final class StompDeliveryImpl implements StompDelivery {

    private final Message message;
    private final Subscription subscription;
    private final Stomp protocol;
    private final Optional<String> session;
    private final Transport transport;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    StompDeliveryImpl(Message message,
                      Subscription subscription,
                      Stomp protocol,
                      Optional<String> session,
                      Transport transport) {
        this.message = message;
        this.subscription = subscription;
        this.protocol = protocol;
        this.session = session;
        this.transport = transport;
    }

    @Override
    public void ack() {
        ensureManualAcknowledgementAllowed();
        if (acknowledged.compareAndSet(false, true)) {
            protocol.acknowledge(message, session, transport);
        }
    }

    @Override
    public boolean acknowledged() {
        return acknowledged.get();
    }

    void autoAcknowledge() {
        if (tryMarkAcknowledged()) {
            protocol.acknowledge(message, session, transport);
        }
    }

    @Override
    public void autoAcknowledgeIfNeeded() {
        if (!subscription.requiresManualAcknowledgement()) {
            autoAcknowledge();
        }
    }

    @Override
    public String body() {
        return message.body();
    }

    @Override
    public String destination() {
        return message.headers().destination().orElse(subscription.topic());
    }

    private void ensureManualAcknowledgementAllowed() {
        if (!subscription.requiresManualAcknowledgement()) {
            throw new StompException("Manual acknowledgement is not enabled for subscription %s".formatted(subscription));
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (Objects.isNull(obj) || obj.getClass() != getClass()) {
            return false;
        }
        var other = (StompDeliveryImpl) obj;
        return Objects.equals(messageId(), other.messageId())
                && Objects.equals(subscription, other.subscription());
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId(), subscription);
    }

    @Override
    public Headers headers() {
        return message.headers();
    }

    boolean isAcknowledged() {
        return acknowledged.get();
    }

    Message message() {
        return message;
    }

    @Override
    public String messageId() {
        return AcknowledgementIds.deliveryMessageId(message, protocol.version()).orElse("");
    }

    @Override
    public void nack() {
        ensureManualAcknowledgementAllowed();
        if (acknowledged.compareAndSet(false, true)) {
            protocol.negativeAcknowledge(message, session, transport);
        }
    }

    @Override
    public Subscription subscription() {
        return subscription;
    }

    @Override
    public String toString() {
        return "StompDelivery[destination=%s, messageId=%s, acknowledged=%s]".formatted(
                                                                                        destination(),
                                                                                        messageId(),
                                                                                        acknowledged.get());
    }

    boolean tryMarkAcknowledged() {
        return acknowledged.compareAndSet(false, true);
    }
}
