package dev.vepo.stomp4j.bridge.internal;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.bridge.DestinationMapper;
import dev.vepo.stomp4j.bridge.KafkaBridgeConfig;
import dev.vepo.stomp4j.server.OutboundChannel;

public final class TopicConsumerManager implements AutoCloseable {
    private static final class DestinationConsumer {
        private static void pollLoop(KafkaBridgeConfig config,
                                     OutboundChannel outboundChannel,
                                     KafkaRecordToStompMapper recordMapper,
                                     String stompDestination,
                                     KafkaConsumer<String, byte[]> consumer,
                                     AtomicBoolean running) {
            while (running.get()) {
                try {
                    var records = consumer.poll(java.time.Duration.ofMillis(config.pollTimeoutMs()));
                    for (var record : records) {
                        var message = recordMapper.toStompMessage(stompDestination, record);
                        outboundChannel.send(message);
                        consumer.commitSync();
                    }
                } catch (WakeupException ex) {
                    if (running.get()) {
                        logger.warn("Kafka consumer wakeup for destination {}", stompDestination, ex);
                    }
                } catch (Exception ex) {
                    logger.error("Kafka consumer error for destination {}", stompDestination, ex);
                }
            }
        }

        static DestinationConsumer start(ExecutorService executor,
                                         KafkaBridgeConfig config,
                                         OutboundChannel outboundChannel,
                                         KafkaRecordToStompMapper recordMapper,
                                         String stompDestination,
                                         String kafkaTopic) {
            var kafkaConsumer = new KafkaConsumer<String, byte[]>(config.consumerPropertiesForTopic(kafkaTopic));
            kafkaConsumer.subscribe(List.of(kafkaTopic));
            var subscribers = new AtomicInteger(1);
            var running = new AtomicBoolean(true);
            var pollTask = executor.submit(() -> pollLoop(config,
                                                          outboundChannel,
                                                          recordMapper,
                                                          stompDestination,
                                                          kafkaConsumer,
                                                          running));
            return new DestinationConsumer(subscribers, kafkaConsumer, running, pollTask);
        }

        private final AtomicInteger subscribers;
        private final KafkaConsumer<String, byte[]> consumer;

        private final AtomicBoolean running;

        private final Future<?> task;

        private DestinationConsumer(AtomicInteger subscribers,
                                    KafkaConsumer<String, byte[]> consumer,
                                    AtomicBoolean running,
                                    Future<?> task) {
            this.subscribers = subscribers;
            this.consumer = consumer;
            this.running = running;
            this.task = task;
        }

        int decrement() {
            return subscribers.decrementAndGet();
        }

        int increment() {
            return subscribers.incrementAndGet();
        }

        void stop() {
            running.set(false);
            consumer.wakeup();
            try {
                task.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                logger.debug("Consumer task ended", ex);
            }
            consumer.close();
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(TopicConsumerManager.class);
    private final DestinationMapper destinationMapper;
    private final KafkaBridgeConfig config;
    private final OutboundChannel outboundChannel;
    private final KafkaRecordToStompMapper recordMapper;
    private final ExecutorService executor;

    private final ConcurrentMap<String, DestinationConsumer> consumers;

    public TopicConsumerManager(DestinationMapper destinationMapper,
                                KafkaBridgeConfig config,
                                OutboundChannel outboundChannel,
                                KafkaRecordToStompMapper recordMapper) {
        this.destinationMapper = destinationMapper;
        this.config = config;
        this.outboundChannel = outboundChannel;
        this.recordMapper = recordMapper;
        this.executor = Executors.newCachedThreadPool(r -> {
            var thread = new Thread(r, "stomp4j-kafka-bridge-consumer");
            thread.setDaemon(true);
            return thread;
        });
        this.consumers = new ConcurrentHashMap<>();
    }

    @Override
    public void close() {
        consumers.values().forEach(DestinationConsumer::stop);
        consumers.clear();
        executor.shutdownNow();
    }

    public void subscribe(String stompDestination) {
        destinationMapper.toKafkaTopic(stompDestination).ifPresentOrElse(kafkaTopic -> consumers.compute(stompDestination,
                                                                                                         (destination, existing) -> {
                                                                                                             if (Objects.isNull(existing)) {
                                                                                                                 return DestinationConsumer.start(executor,
                                                                                                                                                  config,
                                                                                                                                                  outboundChannel,
                                                                                                                                                  recordMapper,
                                                                                                                                                  destination,
                                                                                                                                                  kafkaTopic);
                                                                                                             }
                                                                                                             existing.increment();
                                                                                                             return existing;
                                                                                                         }),
                                                                         () -> logger.warn("Cannot subscribe to unmapped destination {}",
                                                                                           stompDestination));
    }

    public void unsubscribe(String stompDestination) {
        consumers.computeIfPresent(stompDestination, (destination, consumer) -> {
            if (consumer.decrement() <= 0) {
                consumer.stop();
                return null;
            }
            return consumer;
        });
    }
}
