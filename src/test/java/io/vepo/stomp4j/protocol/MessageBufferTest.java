package io.vepo.stomp4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

public class MessageBufferTest {

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
        assertEquals("", message.payload());
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
        assertEquals("Hello, World!", message.payload());
        assertTrue(buffer.append("""
                                 destination:/topic/test-2
                                 message-id:1235
                                 content-type:text/plain

                                 Hello, World! 2
                                 \u0000"""));
        message = buffer.message();
        assertNotNull(message);
        assertEquals(Command.MESSAGE, message.command());
        assertEquals("/topic/test-2", message.headers().get(Header.DESTINATION).orElse(null));
        assertEquals("1235", message.headers().get(Header.MESSAGE_ID).orElse(null));
        assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
        assertEquals("Hello, World! 2", message.payload());
    }

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
        assertEquals("Hello, World!", message.payload());
        assertTrue(buffer.hasMessage());
        message = buffer.message();
        assertNotNull(message);
        assertEquals(Command.MESSAGE, message.command());
        assertEquals("/topic/test-2", message.headers().get(Header.DESTINATION).orElse(null));
        assertEquals("1235", message.headers().get(Header.MESSAGE_ID).orElse(null));
        assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
        assertEquals("Hello, World! 2", message.payload());
        assertFalse(buffer.hasMessage());
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
        assertEquals("Hello, World!", message.payload());
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
                     assertTrue(buffer.append("\u0000\n"));
                     var message = buffer.message();
                     assertNotNull(message);
                     assertEquals(Command.MESSAGE, message.command());
                     assertEquals("/topic/test", message.headers().get(Header.DESTINATION).orElse(null));
                     assertEquals(Integer.toString(index), message.headers().get(Header.MESSAGE_ID).orElse(null));
                     assertEquals("text/plain", message.headers().get(Header.CONTENT_TYPE).orElse(null));
                     assertEquals("Hello, World! " + index, message.payload());
                 });
    }
}
