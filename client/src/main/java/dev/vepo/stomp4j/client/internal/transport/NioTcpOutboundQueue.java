package dev.vepo.stomp4j.client.internal.transport;

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
 * <b>Collaborators:</b> {@link NioTcpTransport}
 * </p>
 * <p>
 * <b>Not responsible for:</b> {@link java.nio.channels.SelectionKey} interest
 * ops or selector wakeup.
 * </p>
 */
final class NioTcpOutboundQueue {

    private final Queue<ByteBuffer> pending = new ArrayDeque<>();
    private ByteBuffer current;

    synchronized boolean drain(SocketChannel socket) throws IOException {
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

    synchronized void enqueue(byte[] frame) {
        pending.add(ByteBuffer.wrap(frame));
    }

    synchronized boolean hasPending() {
        return Objects.nonNull(current) || !pending.isEmpty();
    }

    synchronized byte[] pollFrame() {
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
