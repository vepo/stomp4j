package io.vepo.stomp4j.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageBuffer {
    private static final Logger logger = LoggerFactory.getLogger(MessageBuffer.class);
    private final StringBuilder buffer;

    public MessageBuffer() {
        this.buffer = new StringBuilder();
    }

    public boolean append(String messageLine) {
        buffer.append(messageLine);
        return messageLine.contains(Message.END);
    }

    public Message message() {
        int endOfMessage = buffer.indexOf(Message.END);
        if (endOfMessage > 0) {
            String message = buffer.substring(0, endOfMessage);
            buffer.delete(0, endOfMessage + 2);
            return Message.readMessage(message);
        } else {
            throw new IllegalStateException("Message not complete");
        }
    }

    public boolean hasMessage() {
        return buffer.indexOf(Message.END) > 0;
    }
}
