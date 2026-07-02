package dev.vepo.stomp4j.commons.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Queue encoded STOMP frames and drain them to a non-blocking
 * {@link SocketChannel}, retrying partial writes.</li>
 * </ul>
 * <p>
 * <b>Not responsible for:</b> {@link java.nio.channels.SelectionKey} interest
 * ops or selector wakeup.
 * </p>
 */
public final class TcpOutboundQueue {

    private final Queue<ByteBuffer> pending = new ArrayDeque<>();
    private ByteBuffer current;

    public synchronized boolean drain(SocketChannel socket) throws IOException {
        while (true) {
            if (Objects.isNull(current)) {
                current = pending.poll();
            }
            if (Objects.isNull(current)) {
                return false;
            }
            socket.write(current);
            if (current.hasRemaining()) {
                return true;
            }
            current = null;
        }
    }

    public synchronized void enqueue(byte[] frame) {
        pending.add(ByteBuffer.wrap(frame));
    }

    public synchronized boolean hasPending() {
        return Objects.nonNull(current) || !pending.isEmpty();
    }

    /**
     * Removes the next queued frame for TLS {@code wrap} when plaintext cannot be
     * written directly to the socket.
     */
    public synchronized byte[] pollFrame() {
        if (Objects.nonNull(current)) {
            var bytes = new byte[current.remaining()];
            current.get(bytes);
            current = null;
            return bytes;
        }
        var next = pending.poll();
        if (Objects.isNull(next)) {
            return null;
        }
        var bytes = new byte[next.remaining()];
        next.get(bytes);
        return bytes;
    }
}
