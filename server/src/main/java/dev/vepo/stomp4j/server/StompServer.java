package dev.vepo.stomp4j.server;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.auth.StompAuthenticator;
import dev.vepo.stomp4j.server.channels.Channel;
import dev.vepo.stomp4j.server.channels.ChannelListener;

public class StompServer implements AutoCloseable {
    public static class Builder {
        private Set<TransportChannel> channels;
        private StompAuthenticator authenticator;
        private MessageHandler messageHandler;

        private Builder() {
            this.channels = new HashSet<>();
            this.authenticator = null;
        }

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

        public StompServer start() {
            var server = new StompServer(this);
            server.start();
            return server;
        }
    }

    private class ServerChannelListener implements ChannelListener {

        @Override
        public void messageReceived(Message message) {
            StompServer.this.messageHandler.process(message.headers().destination().orElse(""), message.body(), null);
        }
    }

    private class AllChannelsOutbound implements OutboundChannel {

        @Override
        public void send(Message message) {
            logger.info("Message received! Sending to all channels! channels={} message={}", channels, message);
            StompServer.this.activeChannels.forEach(channel -> channel.outboundChannel()
                                                                      .send(message));
        }

    }

    private static final Logger logger = LoggerFactory.getLogger(StompServer.class);

    public static Builder builder() {
        return new Builder();
    }

    private final Set<TransportChannel> channels;
    private StompAuthenticator authenticator;
    private final AtomicBoolean running;
    private List<Channel> activeChannels;
    private MessageHandler messageHandler;
    private final ChannelListener listener;
    private final OutboundChannel outboundChannel;

    public StompServer() {
        this.channels = new HashSet<>();
        this.authenticator = null;
        this.running = new AtomicBoolean(false);
        this.activeChannels = null;
        this.messageHandler = null;
        this.listener = new ServerChannelListener();
        this.outboundChannel = new AllChannelsOutbound();
    }

    private StompServer(Builder builder) {
        this.channels = builder.channels;
        this.authenticator = builder.authenticator;
        this.running = new AtomicBoolean(false);
        this.activeChannels = null;
        this.messageHandler = builder.messageHandler;
        this.listener = new ServerChannelListener();
        this.outboundChannel = new AllChannelsOutbound();
    }

    public StompServer withChannel(TransportChannel channel) {
        this.channels.add(channel);
        return this;
    }

    public StompServer withAuthenticator(StompAuthenticator authenticator) {
        this.authenticator = authenticator;
        return this;
    }

    public synchronized void start() {
        if (!this.running.get()) {
            if (channels.isEmpty()) {
                throw new IllegalArgumentException("No channel is defined!");
            }
            this.activeChannels = this.channels.stream()
                                               .map(channel -> Channel.load(channel, this.listener))
                                               .toList();
            this.activeChannels.forEach(Channel::start);
            this.running.set(true);
        }
    }

    @Override
    public void close() {
        if (this.running.get() && Objects.nonNull(this.activeChannels)) {
            this.activeChannels.forEach(Channel::close);
        }
    }

    public OutboundChannel outboundChannel() {
        return this.outboundChannel;
    }
}
