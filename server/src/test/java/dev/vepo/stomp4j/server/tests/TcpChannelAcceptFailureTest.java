package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
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
import dev.vepo.stomp4j.server.channels.TcpChannel;
import dev.vepo.stomp4j.server.session.Session;
import dev.vepo.stomp4j.server.session.SessionConfig;
import dev.vepo.stomp4j.server.tests.infra.EphemeralPorts;

@Execution(ExecutionMode.SAME_THREAD)
class TcpChannelAcceptFailureTest {

    private static void invokeAcceptSession(TcpChannel tcpChannel, SelectionKey acceptKey) throws Exception {
        var method = TcpChannel.class.getDeclaredMethod("acceptSession", SelectionKey.class);
        method.setAccessible(true);
        method.invoke(tcpChannel, acceptKey);
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

    private static int readFromClient(SocketChannel client) throws Exception {
        client.configureBlocking(false);
        return client.read(ByteBuffer.allocate(1));
    }

    @SuppressWarnings("unchecked")
    private static Map<Session, ?> sessionAttachments(TcpChannel tcpChannel) throws Exception {
        var field = TcpChannel.class.getDeclaredField("sessionAttachments");
        field.setAccessible(true);
        return (Map<Session, ?>) field.get(tcpChannel);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        if (target == null)
            throw new IllegalArgumentException("target");
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private TcpChannel channel;

    private Selector selector;

    private ServerSocketChannel serverChannel;

    @Test
    @DisplayName("TcpChannel closes accepted socket when session setup fails after accept")
    void shouldCloseAcceptedSocketWhenSessionSetupFails() throws Exception {
        var port = EphemeralPorts.allocate();
        var heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            channel = new TcpChannel(port, noopListener(), new ChannelRuntime(
                                                                              SessionConfig.defaults(),
                                                                              Optional.empty(),
                                                                              heartbeatExecutor));

            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(port));
            var acceptKey = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            setField(channel, "selector", selector);
            setField(channel, "channel", serverChannel);

            try (var client = SocketChannel.open()) {
                client.configureBlocking(true);
                client.connect(new InetSocketAddress("localhost", port));

                selector.selectNow();
                assertThat(acceptKey.isAcceptable()).isTrue();

                selector.close();

                invokeAcceptSession(channel, acceptKey);

                assertThat(sessionAttachments(channel)).isEmpty();
                assertThat(readFromClient(client)).isEqualTo(-1);
            }
        } finally {
            heartbeatExecutor.shutdownNow();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null) {
            channel.close();
        }
        if (selector != null && selector.isOpen()) {
            selector.close();
        }
        if (serverChannel != null && serverChannel.isOpen()) {
            serverChannel.close();
        }
    }
}
