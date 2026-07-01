package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
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
import dev.vepo.stomp4j.server.channels.TcpChannel;
import dev.vepo.stomp4j.server.session.Session;
import dev.vepo.stomp4j.server.session.SessionConfig;
import dev.vepo.stomp4j.server.tests.infra.EphemeralPorts;

@Execution(ExecutionMode.SAME_THREAD)
class TcpChannelStartupFailureTest {

    private TcpChannel channel;

    private ServerSocketChannel portHolder;

    @Test
    @DisplayName("TcpChannel rolls back selector and server channel when bind fails")
    void shouldRollbackResourcesWhenBindFails() throws Exception {
        var port = EphemeralPorts.allocate();
        var heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        portHolder = ServerSocketChannel.open();
        try {
            portHolder.bind(new InetSocketAddress(port));

            channel = new TcpChannel(port, noopListener(), new ChannelRuntime(
                    SessionConfig.defaults(),
                    Optional.empty(),
                    heartbeatExecutor));

            assertThatThrownBy(channel::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(String.valueOf(port));

            assertThat(running(channel)).isFalse();
            assertThat(fieldValue(channel, "selector")).isNull();
            assertThat(fieldValue(channel, "channel")).isNull();
        } finally {
            heartbeatExecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("StompServer propagates TCP bind failure without leaving the server running")
    void shouldPropagateTcpBindFailureFromStompServer() throws Exception {
        var port = EphemeralPorts.allocate();
        portHolder = ServerSocketChannel.open();
        portHolder.bind(new InetSocketAddress(port));

        assertThatThrownBy(() -> StompServer.builder()
                                            .channel(TransportType.TCP, port)
                                            .handler(message -> {})
                                            .subscription(topic -> true)
                                            .start())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(port));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null) {
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

    private static Object fieldValue(TcpChannel tcpChannel, String name) throws Exception {
        var field = TcpChannel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(tcpChannel);
    }

    private static boolean running(TcpChannel tcpChannel) throws Exception {
        var field = TcpChannel.class.getDeclaredField("running");
        field.setAccessible(true);
        return ((AtomicBoolean) field.get(tcpChannel)).get();
    }
}
