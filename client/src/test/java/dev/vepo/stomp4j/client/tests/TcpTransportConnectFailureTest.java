package dev.vepo.stomp4j.client.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.client.internal.transport.SecureTcpTransport;
import dev.vepo.stomp4j.client.internal.transport.TcpTransport;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.protocol.Message;

@Execution(ExecutionMode.SAME_THREAD)
class TcpTransportConnectFailureTest {

    private ExecutorService acceptExecutor;

    @Test
    @DisplayName("TcpTransport leaves no open socket when connect is refused")
    void shouldCloseSocketWhenConnectRefused() throws Exception {
        int port;
        try (var unused = new ServerSocket(0)) {
            port = unused.getLocalPort();
        }

        var transport = new TcpTransport(URI.create("stomp://127.0.0.1:%d".formatted(port)), noopListener());

        assertThatThrownBy(transport::connect).isInstanceOf(StompException.class);
        assertSocketReleased(transport);
        transport.close();
    }

    @Test
    @DisplayName("TcpTransport closes socket when listener rejects connection")
    void shouldCloseSocketWhenListenerRejectsConnection() throws Exception {
        try (var server = new ServerSocket(0)) {
            var port = server.getLocalPort();
            acceptExecutor = Executors.newSingleThreadExecutor();
            acceptExecutor.submit(() -> {
                try (var accepted = server.accept()) {
                    // hold connection until transport aborts
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });

            var transport = new TcpTransport(URI.create("stomp://127.0.0.1:%d".formatted(port)),
                    rejectingListener());

            assertThatThrownBy(transport::connect).isInstanceOf(StompException.class);
            assertSocketReleased(transport);
            transport.close();
        }
    }

    @Test
    @DisplayName("SecureTcpTransport closes socket when TLS handshake fails")
    void shouldCloseSocketWhenSslHandshakeFails() throws Exception {
        try (var server = new ServerSocket(0)) {
            var port = server.getLocalPort();
            acceptExecutor = Executors.newSingleThreadExecutor();
            acceptExecutor.submit(() -> {
                try (var accepted = server.accept()) {
                    // plain TCP endpoint rejects TLS handshake
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });

            var transport = new SecureTcpTransport(URI.create("stomps://127.0.0.1:%d".formatted(port)),
                    noopListener());

            assertThatThrownBy(transport::connect).isInstanceOf(StompException.class);
            assertSocketReleased(transport);
            transport.close();
        }
    }

    @AfterEach
    void tearDown() {
        if (acceptExecutor != null) {
            acceptExecutor.shutdownNow();
            acceptExecutor = null;
        }
    }

    private static void assertSocketReleased(Object transport) throws Exception {
        assertThat(fieldValue(transport, "socket")).isNull();
    }

    private static Object fieldValue(Object target, String name) throws Exception {
        var field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException ex) {
            var superType = type.getSuperclass();
            if (superType == null) {
                throw ex;
            }
            return findField(superType, name);
        }
    }

    private static TransportListener noopListener() {
        return new TransportListener() {
            @Override
            public void onConnected(Transport transport) {}

            @Override
            public void onError(Message message) {}

            @Override
            public void onMessage(Message message) {}
        };
    }

    private static TransportListener rejectingListener() {
        return new TransportListener() {
            @Override
            public void onConnected(Transport transport) {
                throw new StompException("Rejecting connection");
            }

            @Override
            public void onError(Message message) {}

            @Override
            public void onMessage(Message message) {}
        };
    }
}
