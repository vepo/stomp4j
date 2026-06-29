package dev.vepo.stomp4j.bridge;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.SSLContext;

import dev.vepo.stomp4j.bridge.internal.BridgeMessageHandler;
import dev.vepo.stomp4j.bridge.internal.KafkaProducerFacade;
import dev.vepo.stomp4j.bridge.internal.KafkaRecordToStompMapper;
import dev.vepo.stomp4j.bridge.internal.StompToKafkaRecordMapper;
import dev.vepo.stomp4j.bridge.internal.TopicConsumerManager;
import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.server.StompServer;
import dev.vepo.stomp4j.server.StompSession;
import dev.vepo.stomp4j.server.SubscriptionHandler;
import dev.vepo.stomp4j.server.auth.StompAuthenticator;

public final class StompKafkaBridge implements AutoCloseable {
    public static final class Builder {
        private final Set<TransportChannel> channels = new HashSet<>();
        private KafkaBridgeConfig kafkaConfig;
        private DestinationMapper destinationMapper = DestinationMapping.prefix("/topic/");
        private String allowedDestinationPrefix = "/topic/";
        private StompAuthenticator authenticator;
        private Duration heartbeat = Duration.ofSeconds(30);
        private String serverName = "stomp4j-kafka-bridge";
        private SSLContext sslContext;
        private String sslKeyStorePath;
        private String sslKeyStorePassword;

        private Builder() {}

        public Builder allowedDestinationPrefix(String allowedDestinationPrefix) {
            this.allowedDestinationPrefix = allowedDestinationPrefix;
            return this;
        }

        public Builder authenticator(StompAuthenticator authenticator) {
            this.authenticator = authenticator;
            return this;
        }

        public Builder channel(TransportType type, int port) {
            channels.add(new TransportChannel(type, port));
            return this;
        }

        public Builder destinationMapping(DestinationMapper destinationMapper) {
            this.destinationMapper = Objects.requireNonNull(destinationMapper);
            return this;
        }

        public Builder heartbeat(Duration heartbeat) {
            this.heartbeat = heartbeat;
            return this;
        }

        public Builder kafkaConfig(KafkaBridgeConfig kafkaConfig) {
            this.kafkaConfig = kafkaConfig;
            return this;
        }

        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        public Builder ssl(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder ssl(SSLContext sslContext, String keyStorePath, String keyStorePassword) {
            this.sslContext = sslContext;
            this.sslKeyStorePath = keyStorePath;
            this.sslKeyStorePassword = keyStorePassword;
            return this;
        }

        public StompKafkaBridge start() {
            Objects.requireNonNull(kafkaConfig, "kafkaConfig is required");
            if (channels.isEmpty()) {
                throw new IllegalArgumentException("At least one channel is required");
            }
            var bridge = new StompKafkaBridge(this);
            bridge.start();
            return bridge;
        }
    }

    public record TransportChannel(TransportType type, int port) {}

    public static Builder builder() {
        return new Builder();
    }

    private final Set<TransportChannel> channels;
    private final KafkaBridgeConfig kafkaConfig;
    private final DestinationMapper destinationMapper;
    private final String allowedDestinationPrefix;
    private final StompAuthenticator authenticator;
    private final Duration heartbeat;
    private final String serverName;
    private final SSLContext sslContext;
    private final String sslKeyStorePath;
    private final String sslKeyStorePassword;
    private final CountDownLatch shutdownLatch;

    private KafkaProducerFacade producer;
    private TopicConsumerManager consumerManager;
    private StompServer server;

    private StompKafkaBridge(Builder builder) {
        this.channels = Set.copyOf(builder.channels);
        this.kafkaConfig = builder.kafkaConfig;
        this.destinationMapper = builder.destinationMapper;
        this.allowedDestinationPrefix = builder.allowedDestinationPrefix;
        this.authenticator = builder.authenticator;
        this.heartbeat = builder.heartbeat;
        this.serverName = builder.serverName;
        this.sslContext = builder.sslContext;
        this.sslKeyStorePath = builder.sslKeyStorePath;
        this.sslKeyStorePassword = builder.sslKeyStorePassword;
        this.shutdownLatch = new CountDownLatch(1);
    }

    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    private SubscriptionHandler bridgeSubscriptionHandler() {
        return new SubscriptionHandler() {
            @Override
            public boolean accept(String topic) {
                if (!allowedDestinationPrefix.isEmpty() && !topic.startsWith(allowedDestinationPrefix)) {
                    return false;
                }
                return destinationMapper.toKafkaTopic(topic).isPresent();
            }

            @Override
            public void onSubscribed(StompSession session, String topic) {
                if (Objects.nonNull(consumerManager)) {
                    consumerManager.subscribe(topic);
                }
            }

            @Override
            public void onUnsubscribed(StompSession session, String topic) {
                if (Objects.nonNull(consumerManager)) {
                    consumerManager.unsubscribe(topic);
                }
            }
        };
    }

    @Override
    public void close() {
        if (Objects.nonNull(server)) {
            server.close();
            server = null;
        }
        if (Objects.nonNull(consumerManager)) {
            consumerManager.close();
            consumerManager = null;
        }
        if (Objects.nonNull(producer)) {
            producer.close();
            producer = null;
        }
        shutdownLatch.countDown();
    }

    private void start() {
        producer = new KafkaProducerFacade(kafkaConfig);
        var messageHandler = new BridgeMessageHandler(destinationMapper,
                                                      producer,
                                                      new StompToKafkaRecordMapper());
        var subscriptionHandler = bridgeSubscriptionHandler();

        var serverBuilder = StompServer.builder()
                                       .serverName(serverName)
                                       .heartbeat(heartbeat)
                                       .handler(messageHandler)
                                       .subscription(subscriptionHandler);
        channels.forEach(channel -> serverBuilder.channel(channel.type(), channel.port()));
        if (Objects.nonNull(authenticator)) {
            serverBuilder.authenticator(authenticator);
        }
        if (Objects.nonNull(sslContext)) {
            if (Objects.nonNull(sslKeyStorePath)) {
                serverBuilder.ssl(sslContext, sslKeyStorePath, sslKeyStorePassword);
            } else {
                serverBuilder.ssl(sslContext);
            }
        }

        server = serverBuilder.start();
        consumerManager = new TopicConsumerManager(destinationMapper,
                                                   kafkaConfig,
                                                   server.outboundChannel(),
                                                   new KafkaRecordToStompMapper());
    }

    public StompServer stompServer() {
        return server;
    }
}
