package dev.vepo.stomp4j.client.internal.transport;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.protocol.MessageBuffer;

final class WebSocketInboundFramer {

    private final MessageBuffer messageBuffer = new MessageBuffer();
    private volatile long lastReceivedMessage = System.nanoTime();

    void offer(byte[] data, int offset, int length, TransportListener listener) {
        if (length <= 0) {
            return;
        }
        lastReceivedMessage = System.nanoTime();
        if (messageBuffer.append(data, offset, length)) {
            while (messageBuffer.hasMessage()) {
                listener.onMessage(messageBuffer.message());
            }
        }
    }

    void offer(byte[] data, TransportListener listener) {
        offer(data, 0, data.length, listener);
    }

    void offer(CharSequence data, TransportListener listener) {
        offer(data.toString().getBytes(StandardCharsets.UTF_8), listener);
    }

    long silentTime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastReceivedMessage);
    }
}
