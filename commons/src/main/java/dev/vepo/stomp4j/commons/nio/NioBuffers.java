package dev.vepo.stomp4j.commons.nio;

import java.nio.ByteBuffer;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Grow and reset {@link ByteBuffer} instances used by NIO
 * TCP/TLS session code.</li>
 * </ul>
 */
public final class NioBuffers {

    public static ByteBuffer allocate(int size) {
        return ByteBuffer.allocate(size);
    }

    public static ByteBuffer enlarge(ByteBuffer buffer, int newCapacity) {
        var enlarged = ByteBuffer.allocate(newCapacity);
        buffer.flip();
        enlarged.put(buffer);
        return enlarged;
    }

    public static void prepareForSocketRead(ByteBuffer buffer) {
        buffer.clear();
        buffer.limit(0);
    }

    private NioBuffers() {}
}
