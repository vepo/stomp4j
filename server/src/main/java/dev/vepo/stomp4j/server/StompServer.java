package dev.vepo.stomp4j.server;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import dev.vepo.stomp4j.server.ssl.SslSettings;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.auth.StompAuthenticator;
import dev.vepo.stomp4j.server.AcknowledgedOutboundChannel;
import dev.vepo.stomp4j.server.SubscriberAckListener;
import dev.vepo.stomp4j.server.channels.Channel;
import dev.vepo.stomp4j.server.channels.ChannelListener;
import dev.vepo.stomp4j.server.channels.ChannelRuntime;
import dev.vepo.stomp4j.server.session.Session;
import dev.vepo.stomp4j.server.session.SessionConfig;

public class StompServer implements AutoCloseable {
    public static class Builder {
        private final Set<TransportChannel> channels = new HashSet<>();
        private StompAuthenticator authenticator;
        private MessageHandler messageHandler;
        private SubscriptionHandler subscriptionHandler;
        private StompConnectionListener connectionListener = new StompConnectionListener() {};
        private List<String> supportedVersions = SessionConfig.DEFAULT_VERSIONS;
        private Duration heartbeat = SessionConfig.DEFAULT_HEARTBEAT;
        private String serverName = SessionConfig.DEFAULT_SERVER_NAME;
        private SslSettings sslSettings;

        private Builder() {}

        public Builder channel(TransportType type, int port) {
            Objects.requireNonNull(type, "type cannot be null!");
            channels.add(new TransportChannel(type, port));
            return this;
        }

        public Builder authenticator(StompAuthenticator authenticator) {
            this.authenticator = authenticator;
            return this;
        }

        public Builder handler(MessageHandler messageHandler) {
            this.messageHandler = messageHandler;
            return this;
        }

        public Builder subscription(SubscriptionHandler subscriptionHandler) {
            this.subscriptionHandler = subscriptionHandler;
            return this;
        }

        public Builder connectionListener(StompConnectionListener connectionListener) {
            this.connectionListener = Objects.requireNonNull(connectionListener);
            return this;
        }

        public Builder supportedVersions(String... versions) {
            this.supportedVersions = List.of(versions);
            return this;
        }

        public Builder heartbeat(Duration heartbeat) {
            this.heartbeat = Objects.requireNonNull(heartbeat);
            return this;
        }

        public Builder serverName(String serverName) {
            this.serverName = Objects.requireNonNull(serverName);
            return this;
        }

        public Builder ssl(SSLContext sslContext) {
            this.sslSettings = new SslSettings(sslContext, Optional.empty());
            return this;
        }

        public Builder ssl(SSLContext sslContext, String keyStorePath, String keyStorePassword) {
            this.sslSettings = new SslSettings(sslContext,
                                               Optional.of(new SslSettings.KeyStoreLocation(keyStorePath,
                                                                                            keyStorePassword)));
            return this;
        }

        public StompServer start() {
            Objects.requireNonNull(messageHandler, "messageHandler is required");
            Objects.requireNonNull(subscriptionHandler, "subscriptionHandler is required");
            var server = new StompServer(this);
            server.start();
            return server;
        }
    }

    private class ServerChannelListener implements ChannelListener {

        @Override
        public void inboundMessageReceived(Session session, Message message) {
            messageHandler.onSend(new StompMessage(
                    message.headers().destination().orElse(""),
                    message.body(),
                    message.headers(),
                    session.outboundChannel()));
        }

        @Override
        public boolean subscriptionRequested(Session session, String topic) {
            return subscriptionHandler.accept(topic);
        }

        @Override
        public void sessionConnected(Session session) {
            connectionListener.onConnected(session);
        }

        @Override
        public void sessionDisconnected(Session session) {
            connectionListener.onDisconnected(session);
        }
    }

    private class AllChannelsOutbound implements AcknowledgedOutboundChannel {

        @Override
        public void send(Message message) {
            send(message, null);
        }

        @Override
        public void send(Message message, SubscriberAckListener listener) {
            logger.debug("Broadcasting message to all channels: {}", message);
            activeChannels.forEach(channel -> {
                var outbound = channel.outboundChannel();
                if (outbound instanceof AcknowledgedOutboundChannel acknowledged) {
                    acknowledged.send(message, listener);
                } else {
                    outbound.send(message);
                }
            });
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(StompServer.class);

    public static Builder builder() {
        return new Builder();
    }

    private final Set<TransportChannel> channels;
    private final SessionConfig sessionConfig;
    private final ChannelRuntime channelRuntime;
    private final MessageHandler messageHandler;
    private final SubscriptionHandler subscriptionHandler;
    private final StompConnectionListener connectionListener;
    private final ChannelListener listener;
    private final AcknowledgedOutboundChannel outboundChannel;
    private final ScheduledExecutorService heartbeatExecutor;

    private boolean running;
    private List<Channel> activeChannels;

    private StompServer(Builder builder) {
        this.channels = builder.channels;
        this.messageHandler = builder.messageHandler;
        this.subscriptionHandler = builder.subscriptionHandler;
        this.connectionListener = builder.connectionListener;
        this.sessionConfig = new SessionConfig(
                Objects.isNull(builder.authenticator) ? java.util.Optional.empty()
                                                      : java.util.Optional.of(builder.authenticator),
                builder.supportedVersions,
                builder.heartbeat,
                builder.serverName);
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "stomp4j-server-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        this.channelRuntime = new ChannelRuntime(
                sessionConfig,
                Objects.isNull(builder.sslSettings) ? java.util.Optional.empty()
                                                    : java.util.Optional.of(builder.sslSettings),
                heartbeatExecutor);
        this.listener = new ServerChannelListener();
        this.outboundChannel = new AllChannelsOutbound();
    }

    public synchronized void start() {
        if (!running) {
            if (channels.isEmpty()) {
                throw new IllegalArgumentException("No channel is defined!");
            }
            this.activeChannels = channels.stream()
                                          .map(channel -> Channel.load(channel, listener, channelRuntime))
                                          .toList();
            this.activeChannels.forEach(Channel::start);
            this.running = true;
        }
    }

    @Override
    public void close() {
        if (running && Objects.nonNull(activeChannels)) {
            activeChannels.forEach(Channel::close);
            running = false;
        }
        heartbeatExecutor.shutdown();
        try {
            heartbeatExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public OutboundChannel outboundChannel() {
        return outboundChannel;
    }

    public AcknowledgedOutboundChannel acknowledgedOutboundChannel() {
        return outboundChannel;
    }
}
