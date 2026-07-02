package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.StompServer;
import dev.vepo.stomp4j.server.channels.ChannelListener;
import dev.vepo.stomp4j.server.channels.ChannelRuntime;
import dev.vepo.stomp4j.server.channels.WebSocketChannel;
import dev.vepo.stomp4j.server.session.Session;
import dev.vepo.stomp4j.server.session.SessionConfig;
import dev.vepo.stomp4j.server.tests.infra.EphemeralPorts;

@Execution(ExecutionMode.SAME_THREAD)
class WebSocketChannelStartupFailureTest {

    private WebSocketChannel channel;

    private ServerSocketChannel portHolder;

    @Test
    @DisplayName("WebSocketChannel tears down Vert.x when listen fails on occupied port")
    void shouldCloseVertxWhenListenFails() throws Exception {
        var port = EphemeralPorts.allocate();
        var heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        portHolder = ServerSocketChannel.open();
        try {
            portHolder.bind(new InetSocketAddress(port));

            channel = new WebSocketChannel(port, noopListener(), new ChannelRuntime(
                    SessionConfig.defaults(),
                    Optional.empty(),
                    heartbeatExecutor));

            assertThatThrownBy(channel::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(String.valueOf(port));

            assertThat(running(channel)).isFalse();
            assertThat(fieldValue(channel, "vertx")).isNull();
            assertThat(fieldValue(channel, "server")).isNull();
        } finally {
            heartbeatExecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("StompServer propagates WebSocket listen failure without leaving the server running")
    void shouldPropagateWebSocketListenFailureFromStompServer() throws Exception {
        var port = EphemeralPorts.allocate();
        portHolder = ServerSocketChannel.open();
        portHolder.bind(new InetSocketAddress(port));

        assertThatThrownBy(() -> StompServer.builder()
                                            .channel(TransportType.WEB_SOCKET, port)
                                            .handler(message -> {})
                                            .subscription(topic -> true)
                                            .start())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(port));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null && running(channel)) {
            channel.close();
        }
        if (portHolder != null && portHolder.isOpen()) {
            portHolder.close();
        }
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

    private static Object fieldValue(WebSocketChannel webSocketChannel, String name) throws Exception {
        var field = WebSocketChannel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(webSocketChannel);
    }

    private static boolean running(WebSocketChannel webSocketChannel) throws Exception {
        var field = WebSocketChannel.class.getDeclaredField("running");
        field.setAccessible(true);
        return ((AtomicBoolean) field.get(webSocketChannel)).get();
    }
}
