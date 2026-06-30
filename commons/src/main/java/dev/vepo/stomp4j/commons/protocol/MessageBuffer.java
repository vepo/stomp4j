package dev.vepo.stomp4j.commons.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageBuffer {
    private static final Logger logger = LoggerFactory.getLogger(MessageBuffer.class);
    private final ByteArrayOutputStream buffer;

    public MessageBuffer() {
        this.buffer = new ByteArrayOutputStream();
    }

    public boolean append(byte[] data) {
        return append(data, 0, data.length);
    }

    public boolean append(byte[] data, int offset, int length) {
        buffer.write(data, offset, length);
        return containsNullTerminator();
    }

    public boolean append(String messageLine) {
        return append(messageLine.getBytes(StandardCharsets.UTF_8));
    }

    private boolean containsNullTerminator() {
        return indexOfNullTerminator() > 0;
    }

    public boolean hasMessage() {
        return indexOfNullTerminator() > 0;
    }

    private int indexOfNullTerminator() {
        var frameBytes = buffer.toByteArray();
        for (int index = 0; index < frameBytes.length; index++) {
            if (frameBytes[index] == 0) {
                return index;
            }
        }
        return -1;
    }

    public Message message() {
        var nullIndex = indexOfNullTerminator();
        if (nullIndex <= 0) {
            throw new IllegalStateException("Message not complete");
        }
        var frameBytes = buffer.toByteArray();
        var messageContent = new byte[nullIndex];
        System.arraycopy(frameBytes, 0, messageContent, 0, nullIndex);
        var deleteThrough = nullIndex + 1;
        while (deleteThrough < frameBytes.length
                && (frameBytes[deleteThrough] == '\n' || frameBytes[deleteThrough] == '\r')) {
            deleteThrough++;
        }
        buffer.reset();
        if (deleteThrough < frameBytes.length) {
            buffer.write(frameBytes, deleteThrough, frameBytes.length - deleteThrough);
        }
        logger.info("Message received: {}", new String(messageContent, StandardCharsets.UTF_8));
        return FrameDecoder.decode(messageContent);
    }
}
