package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.vepo.stomp4j.client.SendOptions;
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuffer;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;
import dev.vepo.stomp4j.server.StompServer;

class StompServerProtocolTest {

    private static Message connectFrame() {
        return MessageBuilder.builder(Command.CONNECT)
                             .header(Header.ACCEPT_VERSION, "1.2")
                             .header(Header.HOST, "localhost")
                             .build();
    }

    private static Message readFrame(Socket socket) throws IOException {
        var buffer = new MessageBuffer();
        var input = socket.getInputStream();
        var data = new byte[1024];
        while (true) {
            var length = input.read(data);
            if (length < 0) {
                throw new IOException("Connection closed while reading frame");
            }
            if (length > 0 && buffer.append(data, 0, length) && buffer.hasMessage()) {
                return buffer.message();
            }
        }
    }

    private static void writeFrame(Socket socket, Message message) throws IOException {
        OutputStream output = socket.getOutputStream();
        output.write(message.encode().getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    @Test
    @DisplayName("Server should accept STOMP connect frame")
    @Timeout(value = 10)
    void shouldAcceptStompConnectFrame() throws IOException {
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5520)
                                     .subscription(topic -> true)
                                     .handler(message -> {})
                                     .start();
                var socket = new Socket("localhost", 5520)) {
            writeFrame(socket, MessageBuilder.builder(Command.STOMP)
                                             .header(Header.ACCEPT_VERSION, "1.2")
                                             .header(Header.HOST, "localhost")
                                             .build());
            var response = readFrame(socket);
            assertThat(response.command()).isEqualTo(Command.CONNECTED);
            assertThat(response.headers().get(Header.VERSION)).contains("1.2");
        }
    }

    @Test
    @DisplayName("Transactional SEND should deliver only after COMMIT")
    @Timeout(value = 10)
    void shouldDeferTransactionalSendUntilCommit() throws IOException {
        var received = new AtomicInteger();
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5523)
                                     .subscription(topic -> true)
                                     .handler(message -> received.incrementAndGet())
                                     .start();
                var socket = new Socket("localhost", 5523)) {
            writeFrame(socket, connectFrame());
            readFrame(socket);
            writeFrame(socket, MessageBuilder.builder(Command.SUBSCRIBE)
                                             .header(Header.ID, "1")
                                             .header(Header.DESTINATION, "tx-topic")
                                             .build());
            writeFrame(socket, MessageBuilder.builder(Command.BEGIN)
                                             .header(Header.TRANSACTION, "tx-1")
                                             .build());
            writeFrame(socket, MessageBuilder.builder(Command.SEND)
                                             .header(Header.DESTINATION, "tx-topic")
                                             .header(Header.TRANSACTION, "tx-1")
                                             .header(Header.CONTENT_TYPE, "text/plain")
                                             .body("deferred")
                                             .build());
            await().pollDelay(Duration.ofMillis(300)).until(() -> received.get() == 0);
            writeFrame(socket, MessageBuilder.builder(Command.COMMIT)
                                             .header(Header.TRANSACTION, "tx-1")
                                             .build());
            await().atMost(Duration.ofSeconds(5)).until(() -> received.get() == 1);
        }
    }

    @Test
    @DisplayName("Transactional SEND should be discarded on ABORT")
    @Timeout(value = 10)
    void shouldDiscardTransactionalSendOnAbort() throws IOException {
        var received = new AtomicInteger();
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5524)
                                     .subscription(topic -> true)
                                     .handler(message -> received.incrementAndGet())
                                     .start();
                var socket = new Socket("localhost", 5524)) {
            writeFrame(socket, connectFrame());
            readFrame(socket);
            writeFrame(socket, MessageBuilder.builder(Command.BEGIN)
                                             .header(Header.TRANSACTION, "tx-2")
                                             .build());
            writeFrame(socket, MessageBuilder.builder(Command.SEND)
                                             .header(Header.DESTINATION, "tx-topic")
                                             .header(Header.TRANSACTION, "tx-2")
                                             .header(Header.CONTENT_TYPE, "text/plain")
                                             .body("aborted")
                                             .build());
            writeFrame(socket, MessageBuilder.builder(Command.ABORT)
                                             .header(Header.TRANSACTION, "tx-2")
                                             .build());
            await().pollDelay(Duration.ofMillis(500)).until(() -> received.get() == 0);
        }
    }

    @Test
    @DisplayName("Client SEND should reach server handler with polling subscription")
    @Timeout(value = 10)
    void shouldReceiveInboundSendWithPollingSubscription() {
        var receiveMessages = new LinkedList<String>();
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5525)
                                     .authenticator(credentials -> true)
                                     .subscription(topic -> true)
                                     .handler(message -> receiveMessages.add(message.body()))
                                     .start();
                var client = StompClient.create("stomp://localhost:5525")) {
            client.connect();
            client.subscribe("topic-1");
            client.sendPlain("topic-1", "MESSAGE-1", "text/plain");
            await().atMost(Duration.ofSeconds(5)).until(() -> !receiveMessages.isEmpty());
            assertThat(receiveMessages).containsExactly("MESSAGE-1");
        }
    }

    @Test
    @DisplayName("Server should send ERROR and close when subscription is denied")
    @Timeout(value = 10)
    void shouldRejectDeniedSubscription() throws IOException {
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5521)
                                     .subscription(topic -> !"denied".equals(topic))
                                     .handler(message -> {})
                                     .start();
                var socket = new Socket("localhost", 5521)) {
            writeFrame(socket, connectFrame());
            readFrame(socket);
            writeFrame(socket, MessageBuilder.builder(Command.SUBSCRIBE)
                                             .header(Header.ID, "1")
                                             .header(Header.DESTINATION, "denied")
                                             .build());
            var response = readFrame(socket);
            assertThat(response.command()).isEqualTo(Command.ERROR);
            await().atMost(Duration.ofSeconds(2)).until(() -> socket.getInputStream().read() == -1);
        }
    }

    @Test
    @DisplayName("Server should send RECEIPT after SEND with receipt header")
    @Timeout(value = 10)
    void shouldSendReceiptForSend() {
        var received = new LinkedList<String>();
        try (var server = StompServer.builder()
                                     .channel(TransportType.TCP, 5522)
                                     .subscription(topic -> true)
                                     .handler(message -> received.add(message.body()))
                                     .start();
                var client = StompClient.create("stomp://localhost:5522")) {
            client.connect();
            var receipt = client.send("topic-receipt",
                                      "payload",
                                      SendOptions.builder().receipt(true).build());
            await().atMost(Duration.ofSeconds(5)).until(() -> !received.isEmpty());
            assertThat(received).containsExactly("payload");
            await().atMost(Duration.ofSeconds(5)).until(() -> {
                try {
                    receipt.completion().get(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            });
            assertThat(receipt.receiptId()).isNotBlank();
        }
    }
}
