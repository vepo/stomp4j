package dev.vepo.stomp4j.client.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.StompDelivery;
import dev.vepo.stomp4j.client.SubscribeOptions;
import dev.vepo.stomp4j.client.Subscription;
import dev.vepo.stomp4j.client.protocol.Stomp;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Knowing:</b> Callback, delivery, and polling subscriptions for one
 * connected client.</li>
 * <li><b>Doing:</b> Register subscriptions, route inbound {@code MESSAGE}
 * frames to the correct consumer or poll queue, and remove subscriptions on
 * unsubscribe.</li>
 * </ul>
 * <p>
 * <b>Not responsible for:</b> Wire {@code SUBSCRIBE} frame encoding — delegated
 * to {@link Stomp}; transport I/O.
 * </p>
 */
final class SubscriptionDispatcher {

    private static final class StompSubscription implements Subscription {

        private final String topic;
        private final int id;
        private final AckMode ackMode;
        private final boolean autoAckAfterDelivery;
        private final SubscriptionDispatcher owner;

        private StompSubscription(SubscriptionDispatcher owner,
                                  String topic,
                                  int id,
                                  AckMode ackMode,
                                  boolean autoAckAfterDelivery) {
            this.owner = owner;
            this.topic = topic;
            this.id = id;
            this.ackMode = ackMode;
            this.autoAckAfterDelivery = autoAckAfterDelivery;
        }

        @Override
        public AckMode ackMode() {
            return ackMode;
        }

        @Override
        public boolean autoAckAfterDelivery() {
            return autoAckAfterDelivery;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (Objects.isNull(obj) || obj.getClass() != getClass()) {
                return false;
            }
            var other = (StompSubscription) obj;
            return id == other.id && Objects.equals(topic, other.topic);
        }

        @Override
        public boolean hasData() {
            var messageQueue = owner.pollingQueues.get(this);
            return Objects.nonNull(messageQueue) && !messageQueue.isEmpty();
        }

        @Override
        public int hashCode() {
            return Objects.hash(topic, id);
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public List<String> poll() {
            var messageQueue = owner.pollingQueues.get(this);
            if (Objects.isNull(messageQueue)) {
                return List.of();
            }
            List<String> messages = new ArrayList<>();
            Message message;
            while ((message = messageQueue.poll()) != null) {
                messages.add(message.body());
            }
            logger.debug("polled messages! {}", messages);
            return messages;
        }

        @Override
        public boolean requiresManualAcknowledgement() {
            return ackMode.requiresManualAcknowledgement() && !autoAckAfterDelivery;
        }

        @Override
        public String topic() {
            return topic;
        }

        @Override
        public String toString() {
            return "Subscription[topic=%s, id=%s]".formatted(topic, id);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionDispatcher.class);

    private final Map<Subscription, Consumer<String>> stringConsumers = new HashMap<>();
    private final Map<Subscription, Consumer<StompDelivery>> deliveryConsumers = new HashMap<>();
    private final Map<Subscription, Queue<Message>> pollingQueues = Collections.synchronizedMap(new HashMap<>());
    private final Set<Subscription> polling = Collections.synchronizedSet(new HashSet<>());
    private final AtomicInteger subscriptionIdSequence = new AtomicInteger(0);
    private final ClientHeartbeat heartbeat;
    private final Supplier<Stomp> protocol;
    private final Supplier<Optional<String>> session;
    private final Supplier<Transport> transport;

    SubscriptionDispatcher(ClientHeartbeat heartbeat,
                           Supplier<Stomp> protocol,
                           Supplier<Optional<String>> session,
                           Supplier<Transport> transport) {
        this.heartbeat = heartbeat;
        this.protocol = protocol;
        this.session = session;
        this.transport = transport;
    }

    void dispatch(Message message) {
        var subscription = findDeliveryConsumerSubscription(message.headers().get(Header.SUBSCRIPTION),
                                                            message.headers().get(Header.DESTINATION));

        if (subscription.isPresent()) {
            var delivery = new StompDeliveryImpl(message,
                                                 subscription.get(),
                                                 protocol.get(),
                                                 session.get(),
                                                 transport.get());
            deliveryConsumers.get(subscription.get()).accept(delivery);
            if (subscription.get().autoAckAfterDelivery() && !delivery.isAcknowledged()) {
                delivery.autoAcknowledge();
            }
            logger.debug("Delivery consumer found! {}", subscription);
            return;
        }

        subscription = findStringConsumerSubscription(message.headers().get(Header.SUBSCRIPTION),
                                                      message.headers().get(Header.DESTINATION));

        if (subscription.isPresent()) {
            stringConsumers.get(subscription.get()).accept(message.body());
            if (subscription.get().autoAckAfterDelivery()) {
                heartbeat.scheduleOutbound(() -> protocol.get().acknowledge(message, session.get(), transport.get()));
            }
            logger.debug("Consumer found! {}", subscription);
            return;
        }

        subscription = findPollingSubscription(message.headers().get(Header.SUBSCRIPTION),
                                               message.headers().get(Header.DESTINATION));
        logger.debug("Subscription found! {}", subscription);
        if (subscription.isPresent()) {
            pollingQueues.computeIfAbsent(subscription.get(), ignored -> new ConcurrentLinkedQueue<>())
                         .add(message);
            if (subscription.get().autoAckAfterDelivery()) {
                heartbeat.scheduleOutbound(() -> protocol.get().acknowledge(message, session.get(), transport.get()));
            }
        } else {
            logger.warn("No subscription found for message: {}", message);
        }
    }

    private Optional<Subscription> findDeliveryConsumerSubscription(Optional<String> subscriptionId,
                                                                    Optional<String> destination) {
        return findSubscription(subscriptionId, destination, deliveryConsumers.keySet());
    }

    private Optional<Subscription> findPollingSubscription(Optional<String> subscriptionId,
                                                           Optional<String> destination) {
        return findSubscription(subscriptionId, destination, polling);
    }

    private Optional<Subscription> findStringConsumerSubscription(Optional<String> subscriptionId,
                                                                  Optional<String> destination) {
        return findSubscription(subscriptionId, destination, stringConsumers.keySet());
    }

    private Optional<Subscription> findSubscription(Optional<String> subscriptionId,
                                                    Optional<String> destination,
                                                    Set<Subscription> subscriptions) {
        return subscriptionId.filter(id -> id.matches("\\d+"))
                             .map(Integer::parseInt)
                             .flatMap(id -> subscriptions.stream()
                                                         .filter(subs -> subs.id() == id)
                                                         .findFirst())
                             .or(() -> subscriptions.stream()
                                                    .filter(subs -> subs.topic()
                                                                        .equals(destination.orElseThrow(() -> new IllegalStateException("No destination found in message"))))
                                                    .findFirst());
    }

    private StompSubscription newSubscription(String topic, AckMode ackMode, boolean autoAckAfterDelivery) {
        return new StompSubscription(this, topic, subscriptionIdSequence.incrementAndGet(), ackMode, autoAckAfterDelivery);
    }

    Subscription subscribe(String topic, Consumer<String> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        var subscription = newSubscription(topic, AckMode.CLIENT, true);
        stringConsumers.put(subscription, consumer);
        protocol.get().subscribe(subscription, session.get(), transport.get(), subscription.ackMode(), Map.of());
        return subscription;
    }

    Subscription subscribe(String topic, SubscribeOptions options) {
        Objects.requireNonNull(options, "options cannot be null");
        var autoAckAfterDelivery = !options.ackMode().requiresManualAcknowledgement();
        var subscription = newSubscription(topic, options.ackMode(), autoAckAfterDelivery);
        polling.add(subscription);
        protocol.get()
                .subscribe(subscription, session.get(), transport.get(), options.ackMode(), options.headers());
        return subscription;
    }

    Subscription subscribe(String topic, SubscribeOptions options, Consumer<StompDelivery> consumer) {
        Objects.requireNonNull(options, "options cannot be null");
        Objects.requireNonNull(consumer, "consumer cannot be null");
        var autoAckAfterDelivery = !options.ackMode().requiresManualAcknowledgement();
        var subscription = newSubscription(topic, options.ackMode(), autoAckAfterDelivery);
        deliveryConsumers.put(subscription, consumer);
        protocol.get()
                .subscribe(subscription, session.get(), transport.get(), options.ackMode(), options.headers());
        return subscription;
    }

    void unsubscribe(String topic) {
        stringConsumers.keySet()
                       .stream()
                       .filter(subscription -> subscription.topic().equals(topic))
                       .toList()
                       .forEach(this::unsubscribe);
        deliveryConsumers.keySet()
                         .stream()
                         .filter(subscription -> subscription.topic().equals(topic))
                         .toList()
                         .forEach(this::unsubscribe);
    }

    void unsubscribe(Subscription subscription) {
        protocol.get().unsubscribe(subscription, transport.get());
        stringConsumers.remove(subscription);
        deliveryConsumers.remove(subscription);
        polling.remove(subscription);
        pollingQueues.remove(subscription);
    }
}
