package dev.vepo.stomp4j.server.channels;

import dev.vepo.stomp4j.server.OutboundChannel;
import dev.vepo.stomp4j.server.TransportChannel;

public interface Channel extends AutoCloseable {

    static Channel load(TransportChannel channel, ChannelListener listener, ChannelRuntime runtime) {
        return switch (channel.type()) {
            case TCP -> new TcpChannel(channel.port(), listener, runtime);
            case WEB_SOCKET -> new WebSocketChannel(channel.port(), listener, runtime);
        };
    }

    void close();

    OutboundChannel outboundChannel();

    void start();
}
