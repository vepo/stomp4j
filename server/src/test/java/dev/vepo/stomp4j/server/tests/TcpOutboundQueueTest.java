package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.server.channels.TcpOutboundQueue;

class TcpOutboundQueueTest {

    @Test
    @DisplayName("TcpOutboundQueue drains a large frame through a small socket send buffer")
    void shouldDrainLargeFrameDespiteSmallSendBuffer() throws Exception {
        try (var serverSocket = ServerSocketChannel.open()) {
            serverSocket.bind(new InetSocketAddress("localhost", 0));
            var port = serverSocket.socket().getLocalPort();

            try (var client = SocketChannel.open()) {
                client.configureBlocking(true);
                client.connect(new InetSocketAddress("localhost", port));

                try (var serverChannel = serverSocket.accept()) {
                    serverChannel.configureBlocking(false);
                    serverChannel.setOption(StandardSocketOptions.SO_SNDBUF, 512);

                    var payload = "x".repeat(4096).getBytes(StandardCharsets.UTF_8);
                    var queue = new TcpOutboundQueue();
                    queue.enqueue(payload);

                    while (queue.hasPending()) {
                        queue.drain(serverChannel);
                    }

                    var received = ByteBuffer.allocate(payload.length);
                    while (received.hasRemaining()) {
                        var read = client.read(received);
                        assertThat(read).isGreaterThan(0);
                    }

                    assertThat(received.array()).isEqualTo(payload);
                }
            }
        }
    }
}
