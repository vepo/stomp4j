package dev.vepo.stomp4j.client.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.client.internal.transport.NioTcpTransport;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuffer;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;

@Execution(ExecutionMode.SAME_THREAD)
class NioTcpTransportConcurrentSendTest {

    private static final int THREAD_COUNT = 8;
    private static final int SENDS_PER_THREAD = 50;

    private static TransportListener connectedListener(CountDownLatch connected) {
        return new TransportListener() {
            @Override
            public void onConnected(Transport transport) {
                connected.countDown();
            }

            @Override
            public void onError(Message message) {}

            @Override
            public void onMessage(Message message) {}
        };
    }

    private static void readPeerFrames(ServerSocket server,
                                       Set<String> receivedBodies,
                                       CountDownLatch peerAccepted,
                                       CountDownLatch peerFinished) {
        try (var socket = server.accept()) {
            peerAccepted.countDown();
            var heartbeatWriter = Executors.newSingleThreadScheduledExecutor();
            heartbeatWriter.scheduleAtFixedRate(() -> {
                try {
                    socket.getOutputStream().write('\n');
                    socket.getOutputStream().flush();
                } catch (IOException ex) {
                    heartbeatWriter.shutdown();
                }
            }, 0, 5, TimeUnit.MILLISECONDS);

            var messageBuffer = new MessageBuffer();
            var readBuffer = new byte[4096];
            try (var inputStream = socket.getInputStream()) {
                int length;
                while ((length = inputStream.read(readBuffer)) != -1) {
                    if (messageBuffer.append(readBuffer, 0, length)) {
                        while (messageBuffer.hasMessage()) {
                            var message = messageBuffer.message();
                            if (message.command() == Command.SEND) {
                                receivedBodies.add(message.body());
                            }
                        }
                    }
                }
            } finally {
                heartbeatWriter.shutdownNow();
                peerFinished.countDown();
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    @DisplayName("NioTcpTransport preserves STOMP frames when many threads send while inbound heartbeats arrive")
    void shouldNotCorruptFramesWhenSendingConcurrentlyWhileReadingInboundHeartbeats() throws Exception {
        try (var server = new ServerSocket(0)) {
            var port = server.getLocalPort();
            var receivedBodies = ConcurrentHashMap.<String>newKeySet();
            var peerAccepted = new CountDownLatch(1);
            var peerFinished = new CountDownLatch(1);

            var peerExecutor = Executors.newSingleThreadExecutor();
            try {
                peerExecutor.submit(() -> readPeerFrames(server, receivedBodies, peerAccepted, peerFinished));

                var connected = new CountDownLatch(1);
                var transport = new NioTcpTransport(URI.create("stomp://127.0.0.1:%d".formatted(port)),
                                                    connectedListener(connected));

                try {
                    transport.connect();
                    assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();

                    var sendPool = Executors.newFixedThreadPool(THREAD_COUNT);
                    try {
                        var tasks = sendPool.invokeAll(
                                                       java.util.stream.IntStream.range(0, THREAD_COUNT)
                                                                                 .mapToObj(threadId -> (java.util.concurrent.Callable<Void>) () -> {
                                                                                     for (var sendIndex = 0; sendIndex < SENDS_PER_THREAD; sendIndex++) {
                                                                                         var body = "thread-%d-send-%d".formatted(threadId, sendIndex);
                                                                                         transport.send(MessageBuilder.builder(Command.SEND)
                                                                                                                      .body(body)
                                                                                                                      .build());
                                                                                     }
                                                                                     return null;
                                                                                 })
                                                                                 .toList());
                        for (var task : tasks) {
                            task.get(30, TimeUnit.SECONDS);
                        }
                    } finally {
                        sendPool.shutdownNow();
                    }
                } finally {
                    transport.close();
                }

                assertThat(peerAccepted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(peerFinished.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(receivedBodies).hasSize(THREAD_COUNT * SENDS_PER_THREAD);
            } finally {
                peerExecutor.shutdownNow();
            }
        }
    }
}
