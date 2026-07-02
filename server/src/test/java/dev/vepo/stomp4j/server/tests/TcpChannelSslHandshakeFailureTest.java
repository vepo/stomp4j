package dev.vepo.stomp4j.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
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
import dev.vepo.stomp4j.server.ssl.SslSettings;
import dev.vepo.stomp4j.server.tests.infra.EphemeralPorts;
import dev.vepo.stomp4j.server.tests.infra.TestSsl;

@Execution(ExecutionMode.SAME_THREAD)
class TcpChannelSslHandshakeFailureTest {

    private static boolean clientDisconnected(Socket client) throws IOException {
        client.setSoTimeout(100);
        try {
            return client.getInputStream().read() == -1;
        } catch (SocketTimeoutException ex) {
            return false;
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

    @SuppressWarnings("unchecked")
    private static Map<Session, ?> sslSessions(TcpChannel tcpChannel) throws Exception {
        var field = TcpChannel.class.getDeclaredField("sslSessions");
        field.setAccessible(true);
        return (Map<Session, ?>) field.get(tcpChannel);
    }

    private TcpChannel channel;

    @Test
    @DisplayName("TcpChannel closes accepted SSL socket when handshake fails")
    void shouldCloseSslSocketWhenHandshakeFails() throws Exception {
        var port = EphemeralPorts.allocate();
        var heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            var sslSettings = new SslSettings(TestSsl.serverSslContext(), Optional.empty());
            channel = new TcpChannel(port, noopListener(), new ChannelRuntime(
                                                                              SessionConfig.defaults(),
                                                                              Optional.of(sslSettings),
                                                                              heartbeatExecutor));
            channel.start();

            try (var client = new Socket()) {
                client.connect(new InetSocketAddress("localhost", port), 2000);
                client.getOutputStream().write("NOT-TLS\n".getBytes());
                client.getOutputStream().flush();

                await().atMost(Duration.ofSeconds(5)).until(() -> clientDisconnected(client));

                assertThat(sslSessions(channel)).isEmpty();
            }
        } finally {
            heartbeatExecutor.shutdownNow();
        }
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.close();
        }
    }
}
