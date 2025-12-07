package dev.vepo.stomp4j.server.channels;

import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.OutboundChannel;

public class WebSocketChannel implements Channel {
    private class WebSocketExternalOutboundChannel implements OutboundChannel {

        @Override
        public void send(Message message) {
            // do nothing for now
        }

    }

    private final WebSocketExternalOutboundChannel outboundChannel;

    public WebSocketChannel(int port, ChannelListener listener) {
        this.outboundChannel = new WebSocketExternalOutboundChannel();
    }

    @Override
    public void start() {

    }

    @Override
    public void close() {

    }

    @Override
    public OutboundChannel outboundChannel() {
        return this.outboundChannel;
    }

}
