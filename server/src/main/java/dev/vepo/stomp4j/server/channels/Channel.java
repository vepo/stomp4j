package dev.vepo.stomp4j.server.channels;

import dev.vepo.stomp4j.server.OutboundChannel;
import dev.vepo.stomp4j.server.TransportChannel;

public interface Channel extends AutoCloseable {
    public static Channel load(TransportChannel channel, ChannelListener listener) {
        return switch(channel.type()) {
            case TCP -> new TcpChannel(channel.port(), listener);
            case WEB_SOCKET -> new WebSocketChannel(channel.port(), listener);
        };
    }

    void start();
    void close();
    OutboundChannel outboundChannel();
}
