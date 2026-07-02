package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.Optional;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.channels.ChannelListener;
import dev.vepo.stomp4j.server.channels.ChannelRuntime;
import dev.vepo.stomp4j.server.channels.WebSocketChannel;
import dev.vepo.stomp4j.server.session.Session;
import dev.vepo.stomp4j.server.session.SessionConfig;
import dev.vepo.stomp4j.server.tests.infra.EphemeralPorts;
import io.vertx.core.Vertx;

@Execution(ExecutionMode.SAME_THREAD)
class WebSocketChannelCloseTest {

    private static Object fieldValue(WebSocketChannel webSocketChannel, String name) throws Exception {
        var field = WebSocketChannel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(webSocketChannel);
    }

    private static ChannelListener noopListener() {
        return new ChannelListener() {
            @Override
            public void inboundMessageReceived(Session session, Message message) {}

            @Override
            public void sessionConnected(Session session) {}

            @Override
            public void sessionDisconnected(Session session) {}

            @Override
            public boolean subscriptionRequested(Session session, String topic) {
                return true;
            }
        };
    }

    private static boolean running(WebSocketChannel webSocketChannel) throws Exception {
        var field = WebSocketChannel.class.getDeclaredField("running");
        field.setAccessible(true);
        return ((java.util.concurrent.atomic.AtomicBoolean) field.get(webSocketChannel)).get();
    }

    private WebSocketChannel channel;

    @Test
    @DisplayName("WebSocketChannel close waits for Vert.x shutdown before returning")
    void shouldAwaitVertxShutdownBeforeCloseReturns() throws Exception {
        var port = EphemeralPorts.allocate();
        var heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            channel = new WebSocketChannel(port, noopListener(), new ChannelRuntime(
                                                                                    SessionConfig.defaults(),
                                                                                    Optional.empty(),
                                                                                    heartbeatExecutor));
            channel.start();

            var vertx = (Vertx) fieldValue(channel, "vertx");
            assertThat(vertx).isNotNull();

            channel.close();

            assertThat(fieldValue(channel, "vertx")).isNull();
            assertThat(fieldValue(channel, "server")).isNull();
            assertThat(vertx.close().isComplete()).isTrue();
            assertThatCode(() -> {
                try (var portProbe = ServerSocketChannel.open()) {
                    portProbe.bind(new InetSocketAddress(port));
                }
            }).doesNotThrowAnyException();
        } finally {
            heartbeatExecutor.shutdownNow();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null && running(channel)) {
            channel.close();
        }
    }
}
