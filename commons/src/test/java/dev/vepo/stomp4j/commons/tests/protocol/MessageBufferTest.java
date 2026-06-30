package dev.vepo.stomp4j.commons.tests.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuffer;

class MessageBufferTest {

    @Test
    void hasMessageTest() {
        var buffer = new MessageBuffer();
        assertTrue(buffer.append("""
                                 MESSAGE
                                 destination:/topic/test
                                 message-id:1234
                                 content-type:text/plain

                                 Hello, World!
                                 \u0000
                                 MESSAGE
                                 destination:/topic/test-2
                                 message-id:1235
                                 content-type:text/plain

                                 Hello, World! 2
                                 \u0000
                                 MESSAGE
                                 """));
        assertTrue(buffer.hasMessage());
        var message = buffer.message();
        assertNotNull(message);
        assertEquals(Command.MESSAGE, message.command());
        assertEquals("/topic/test", message.headers().get(Header.DESTINATION).orElse(null));
        assertEquals("1234", message.headers().get(Header.MESSAGE_ID).orElse(null));
        assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
        assertEquals("Hello, World!", message.body());
        assertTrue(buffer.hasMessage());
        message = nextFrame(buffer);
        assertNotNull(message);
        assertEquals(Command.MESSAGE, message.command());
        assertEquals("/topic/test-2", message.headers().get(Header.DESTINATION).orElse(null));
        assertEquals("1235", message.headers().get(Header.MESSAGE_ID).orElse(null));
        assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
        assertEquals("Hello, World! 2", message.body());
        assertTrue(buffer.hasMessage());
        assertEquals(Command.HEARTBEAT, buffer.message().command());
        assertFalse(buffer.hasMessage());
    }

    @Test
    void heartbeatBeforeFrameTest() {
        var buffer = new MessageBuffer();
        assertTrue(buffer.append("\n"));
        assertEquals(Command.HEARTBEAT, buffer.message().command());
        assertTrue(buffer.append("MESSAGE\ndestination:/topic/test\nmessage-id:1234\ncontent-type:text/plain\n\nHello\n\u0000"));
        assertTrue(buffer.hasMessage());
        var message = buffer.message();
        assertEquals(Command.MESSAGE, message.command());
        assertEquals("/topic/test", message.headers().get(Header.DESTINATION).orElse(null));
        assertEquals("Hello", message.body());
        assertFalse(buffer.hasMessage());
    }

    @Test
    void standaloneHeartbeatTest() {
        var buffer = new MessageBuffer();
        assertTrue(buffer.append("\n"));
        assertTrue(buffer.hasMessage());
        assertEquals(Command.HEARTBEAT, buffer.message().command());
        assertFalse(buffer.hasMessage());
    }

    @Test
    void crlfHeartbeatTest() {
        var buffer = new MessageBuffer();
        assertTrue(buffer.append("\r\n"));
        assertEquals(Command.HEARTBEAT, buffer.message().command());
        assertFalse(buffer.hasMessage());
    }

    @Test
    void heartbeatWithMessageTest() {
        var buffer = new MessageBuffer();
        assertTrue(buffer.append("""
                                 MESSAGE
                                 destination:/topic/test
                                 message-id:1234
                                 content-type:text/plain

                                 Hello, World!
                                 \u0000

                                 MESSAGE
                                 destination:/topic/test-2
                                 message-id:1235
                                 content-type:text/plain

                                 Hello, World! 2
                                 \u0000"""));
        var message = buffer.message();
        assertNotNull(message);
        assertEquals(Command.MESSAGE, message.command());
        assertEquals("/topic/test", message.headers().get(Header.DESTINATION).orElse(null));
        assertEquals("1234", message.headers().get(Header.MESSAGE_ID).orElse(null));
        assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
        assertEquals("Hello, World!", message.body());
        assertTrue(buffer.hasMessage());
        assertEquals(Command.HEARTBEAT, buffer.message().command());
        assertTrue(buffer.hasMessage());
        assertEquals(Command.HEARTBEAT, buffer.message().command());
        message = buffer.message();
        assertNotNull(message);
        assertEquals(Command.MESSAGE, message.command());
        assertEquals("/topic/test-2", message.headers().get(Header.DESTINATION).orElse(null));
        assertEquals("1235", message.headers().get(Header.MESSAGE_ID).orElse(null));
        assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
        assertEquals("Hello, World! 2", message.body());
        assertFalse(buffer.hasMessage());
    }

    @Test
    void linesTest() {
        var buffer = new MessageBuffer();
        assertFalse(buffer.append("""
                                  MESSAGE
                                  destination:/topic/test
                                  message-id:1234
                                  content-type:text/plain

                                  Hello, World!"""));
        assertTrue(buffer.append("""
                                 \u0000
                                 MESSAGE
                                 """));
        var message = buffer.message();
        assertNotNull(message);
        assertEquals(Command.MESSAGE, message.command());
        assertEquals("/topic/test", message.headers().get(Header.DESTINATION).orElse(null));
        assertEquals("1234", message.headers().get(Header.MESSAGE_ID).orElse(null));
        assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
        assertEquals("Hello, World!", message.body());
        assertTrue(buffer.append("""
                                 destination:/topic/test-2
                                 message-id:1235
                                 content-type:text/plain

                                 Hello, World! 2
                                 \u0000"""));
        message = nextFrame(buffer);
        assertNotNull(message);
        assertEquals(Command.MESSAGE, message.command());
        assertEquals("/topic/test-2", message.headers().get(Header.DESTINATION).orElse(null));
        assertEquals("1235", message.headers().get(Header.MESSAGE_ID).orElse(null));
        assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
        assertEquals("Hello, World! 2", message.body());
    }

    @Test
    void manyMessagesTest() {
        var buffer = new MessageBuffer();
        IntStream.range(0, 100)
                 .forEach(index -> {
                     assertFalse(buffer.append("MESSAGE\n"));
                     assertFalse(buffer.append("destination:/topic/test\n"));
                     assertFalse(buffer.append("message-id:" + index + "\n"));
                     assertFalse(buffer.append("content-type:text/plain\n"));
                     assertFalse(buffer.append("\n"));
                     assertFalse(buffer.append("Hello, World! " + index + "\n"));
                     assertTrue(buffer.append("\u0000"));
                     var message = buffer.message();
                     assertNotNull(message);
                     assertEquals(Command.MESSAGE, message.command());
                     assertEquals("/topic/test", message.headers().get(Header.DESTINATION).orElse(null));
                     assertEquals(Integer.toString(index), message.headers().get(Header.MESSAGE_ID).orElse(null));
                     assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
                     assertEquals("Hello, World! " + index, message.body());
                 });
    }

    @Test
    void noLineEndindTest() {
        var buffer = new MessageBuffer();
        assertTrue(buffer.append("""
                                 MESSAGE
                                 destination:/topic/test
                                 message-id:1234
                                 content-type:text/plain

                                 Hello, World!\u0000
                                 MESSAGE
                                 destination:/topic/test-2
                                 message-id:1235
                                 content-type:text/plain

                                 Hello, World! 2\u0000"""));
        var message = buffer.message();
        assertNotNull(message);
        assertEquals(Command.MESSAGE, message.command());
        assertEquals("/topic/test", message.headers().get(Header.DESTINATION).orElse(null));
        assertEquals("1234", message.headers().get(Header.MESSAGE_ID).orElse(null));
        assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
        assertEquals("Hello, World!", message.body());
        assertTrue(buffer.hasMessage());
        message = nextFrame(buffer);
        assertNotNull(message);
        assertEquals(Command.MESSAGE, message.command());
        assertEquals("/topic/test-2", message.headers().get(Header.DESTINATION).orElse(null));
        assertEquals("1235", message.headers().get(Header.MESSAGE_ID).orElse(null));
        assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
        assertEquals("Hello, World! 2", message.body());
        assertFalse(buffer.hasMessage());
    }

    @Test
    void noPayloadMessageTest() {
        var buffer = new MessageBuffer();
        assertFalse(buffer.append("CONNECTED\n"));
        assertFalse(buffer.append("server:ActiveMQ/6.1.0\n"));
        assertFalse(buffer.append("session:ID:50504d4c7318-45533-1713705509309-3:13\n"));
        assertFalse(buffer.append("version:1.2\n"));
        assertFalse(buffer.append("\n"));
        assertTrue(buffer.append("\u0000\n"));
        var message = buffer.message();
        assertNotNull(message);
        assertEquals(Command.CONNECTED, message.command());
        assertEquals("ActiveMQ/6.1.0", message.headers().get(Header.SERVER).orElse(null));
        assertEquals("ID:50504d4c7318-45533-1713705509309-3:13", message.headers().get(Header.SESSION).orElse(null));
        assertEquals("1.2", message.headers().get(Header.VERSION).orElse(null));
        assertEquals("", message.body());
    }

    @Test
    void payloadMessageTest() {
        var buffer = new MessageBuffer();
        assertFalse(buffer.append("MESSAGE\n"));
        assertFalse(buffer.append("destination:/topic/test\n"));
        assertFalse(buffer.append("message-id:1234\n"));
        assertFalse(buffer.append("content-type:text/plain\n"));
        assertFalse(buffer.append("\n"));
        assertFalse(buffer.append("Hello, World!\n"));
        assertTrue(buffer.append("\u0000\n"));
        var message = buffer.message();
        assertNotNull(message);
        assertEquals(Command.MESSAGE, message.command());
        assertEquals("/topic/test", message.headers().get(Header.DESTINATION).orElse(null));
        assertEquals("1234", message.headers().get(Header.MESSAGE_ID).orElse(null));
        assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
        assertEquals("Hello, World!", message.body());
    }

    private static Message nextFrame(MessageBuffer buffer) {
        Message message;
        do {
            message = buffer.message();
        } while (message.command() == Command.HEARTBEAT);
        return message;
    }
}
