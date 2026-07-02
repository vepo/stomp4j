package dev.vepo.stomp4j.commons.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Knowing:</b> Accumulated wire bytes until a complete STOMP frame (NUL
 * terminator) is available.</li>
 * <li><b>Doing:</b> Append stream bytes, detect heart-beat octets, and decode
 * complete frames via {@link FrameDecoder}.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> {@link FrameDecoder}, {@link Message}
 * </p>
 * <p>
 * <b>Not responsible for:</b> Transport I/O, session routing, protocol version
 * negotiation.
 * </p>
 */
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
        return hasMessage();
    }

    public boolean append(String messageLine) {
        return append(messageLine.getBytes(StandardCharsets.UTF_8));
    }

    private void consumeLeadingBytes(int length) {
        var frameBytes = buffer.toByteArray();
        buffer.reset();
        if (length < frameBytes.length) {
            buffer.write(frameBytes, length, frameBytes.length - length);
        }
    }

    private boolean containsNullTerminator() {
        return indexOfNullTerminator() > 0;
    }

    public boolean hasMessage() {
        return leadingHeartbeatLength() > 0 || containsNullTerminator();
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

    // STOMP heart-beat is a lone LF or CRLF on the byte stream — not a
    // NUL-terminated frame.
    private int leadingHeartbeatLength() {
        var frameBytes = buffer.toByteArray();
        if (frameBytes.length == 0) {
            return 0;
        }
        if (frameBytes[0] == '\n') {
            return 1;
        }
        if (frameBytes.length >= 2 && frameBytes[0] == '\r' && frameBytes[1] == '\n') {
            return 2;
        }
        return 0;
    }

    public Message message() {
        var heartbeatLength = leadingHeartbeatLength();
        if (heartbeatLength > 0) {
            consumeLeadingBytes(heartbeatLength);
            logger.debug("Heartbeat received");
            return Message.HEARTBEAT;
        }
        var nullIndex = indexOfNullTerminator();
        if (nullIndex <= 0) {
            throw new IllegalStateException("Message not complete");
        }
        var frameBytes = buffer.toByteArray();
        var messageContent = new byte[nullIndex];
        System.arraycopy(frameBytes, 0, messageContent, 0, nullIndex);
        buffer.reset();
        if (nullIndex + 1 < frameBytes.length) {
            buffer.write(frameBytes, nullIndex + 1, frameBytes.length - nullIndex - 1);
        }
        logger.info("Message received: {}", new String(messageContent, StandardCharsets.UTF_8));
        return FrameDecoder.decode(messageContent);
    }
}
