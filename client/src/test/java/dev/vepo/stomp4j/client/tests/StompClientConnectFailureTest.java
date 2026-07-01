package dev.vepo.stomp4j.client.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.client.internal.StompClientImpl;
import dev.vepo.stomp4j.client.internal.transport.TcpTransport;
import dev.vepo.stomp4j.client.tests.infra.StompActiveMqContainer;
import dev.vepo.stomp4j.client.tests.infra.StompContainer;

@ExtendWith(StompContainer.class)
@Execution(ExecutionMode.SAME_THREAD)
class StompClientConnectFailureTest {

    @Test
    @DisplayName("Failed TCP connect closes transport and heartbeat executor")
    void shouldCleanupAfterTcpConnectRefused() throws Exception {
        int port;
        try (var unused = new ServerSocket(0)) {
            port = unused.getLocalPort();
        }

        var client = StompClient.create("stomp://127.0.0.1:%d".formatted(port));

        assertThatThrownBy(client::connect).isInstanceOf(StompException.class);

        assertClientResourcesReleased(client);
        client.close();
    }

    @Tag("integration")
    @Test
    @DisplayName("Failed STOMP negotiation closes client resources")
    void shouldCleanupAfterAuthenticationFailure(StompActiveMqContainer broker) throws Exception {
        var client = StompClient.create(broker.tcpUrl(), new UserCredential("wrong", "credentials"));

        assertThatThrownBy(client::connect).isInstanceOf(StompException.class);

        assertClientResourcesReleased(client);
        client.close();
    }

    private static void assertClientResourcesReleased(StompClient client) throws Exception {
        assertThat(clientClosed(client)).isTrue();

        var heartBeatService = (ScheduledExecutorService) fieldValue(client, "heartBeatService");
        assertThat(heartBeatService.isShutdown()).isTrue();

        var transport = fieldValue(client, "transport");
        if (transport instanceof TcpTransport tcpTransport) {
            assertThat(tcpTransportRunning(tcpTransport)).isFalse();
            assertThat(tcpTransportExecutor(tcpTransport).isShutdown()).isTrue();
        }
    }

    private static boolean clientClosed(StompClient client) throws Exception {
        var closed = (AtomicBoolean) fieldValue(client, "closed");
        return closed.get();
    }

    private static Object fieldValue(StompClient client, String name) throws Exception {
        var field = StompClientImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(client);
    }

    private static ExecutorService tcpTransportExecutor(TcpTransport transport) throws Exception {
        var field = TcpTransport.class.getDeclaredField("executor");
        field.setAccessible(true);
        return (ExecutorService) field.get(transport);
    }

    private static boolean tcpTransportRunning(TcpTransport transport) throws Exception {
        var field = TcpTransport.class.getDeclaredField("running");
        field.setAccessible(true);
        return ((AtomicBoolean) field.get(transport)).get();
    }
}
