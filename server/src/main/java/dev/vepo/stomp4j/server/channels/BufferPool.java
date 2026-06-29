package dev.vepo.stomp4j.server.channels;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.IntStream;

public class BufferPool {
    private final int poolSize;
    private final int bufferSize;
    private final Queue<ByteBuffer> availableBuffers;
    private final Queue<ByteBuffer> activeBuffers;

    public BufferPool(int poolSize, int bufferSize) {
        this.poolSize = poolSize;
        this.bufferSize = bufferSize;
        this.availableBuffers = new LinkedList<>();
        this.activeBuffers = new LinkedList<>();
        IntStream.range(0, poolSize)
                 .forEach(__ -> availableBuffers.add(ByteBuffer.allocate(bufferSize)));
    }

    public synchronized void release(ByteBuffer buffer) {
        this.activeBuffers.remove(buffer);
        this.availableBuffers.offer(buffer);
        this.notifyAll();
    }

    public synchronized ByteBuffer request() {
        try {
            while (availableBuffers.isEmpty()) {
                this.wait();
            }
            var buffer = this.availableBuffers.poll();
            this.activeBuffers.add(buffer);
            return buffer;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
