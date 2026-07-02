package dev.vepo.stomp4j.client.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.client.internal.transport.NioTcpTransport;
import dev.vepo.stomp4j.client.internal.transport.SecureTcpTransport;
import dev.vepo.stomp4j.client.internal.transport.TcpTransport;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.protocol.Message;

@Execution(ExecutionMode.SAME_THREAD)
class TcpTransportConnectFailureTest {

    private static void assertSocketReleased(Transport transport) throws Exception {
        var fieldName = transport instanceof NioTcpTransport ? "socketChannel" : "socket";
        assertThat(fieldValue(transport, fieldName)).isNull();
    }

    private static Object fieldValue(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Transport newPlainTransport(Class<? extends Transport> type, URI uri, TransportListener listener)
            throws ReflectiveOperationException {
        return type.getConstructor(URI.class, TransportListener.class).newInstance(uri, listener);
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

    private static Stream<Class<? extends Transport>> plainTcpTransports() {
        return Stream.of(TcpTransport.class, NioTcpTransport.class);
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

    private ExecutorService acceptExecutor;

    @ParameterizedTest
    @MethodSource("plainTcpTransports")
    @DisplayName("Plain TCP transport leaves no open socket when connect is refused")
    void shouldCloseSocketWhenConnectRefused(Class<? extends Transport> transportType) throws Exception {
        int port;
        try (var unused = new ServerSocket(0)) {
            port = unused.getLocalPort();
        }

        var transport = newPlainTransport(transportType,
                                          URI.create("stomp://127.0.0.1:%d".formatted(port)),
                                          noopListener());

        assertThatThrownBy(transport::connect).isInstanceOf(StompException.class);
        assertSocketReleased(transport);
        transport.close();
    }

    @ParameterizedTest
    @MethodSource("plainTcpTransports")
    @DisplayName("Plain TCP transport closes socket when listener rejects connection")
    void shouldCloseSocketWhenListenerRejectsConnection(Class<? extends Transport> transportType) throws Exception {
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

            var transport = newPlainTransport(transportType,
                                              URI.create("stomp://127.0.0.1:%d".formatted(port)),
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
}
