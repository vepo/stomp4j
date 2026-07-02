package dev.vepo.stomp4j.client.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.client.internal.transport.WebSocketTransport;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.protocol.Message;

@Execution(ExecutionMode.SAME_THREAD)
class WebSocketTransportConnectFailureTest {

    private static void assertHttpClientClosed(WebSocketTransport transport) throws Exception {
        var httpClient = (HttpClient) fieldValue(transport, "httpClient");
        var uri = URI.create("ws://127.0.0.1:9");
        assertThatThrownBy(() -> httpClient.newWebSocketBuilder()
                                           .buildAsync(uri, new WebSocket.Listener() {})
                                           .join())
                                                   .hasRootCauseInstanceOf(IOException.class)
                                                   .hasRootCauseMessage("closed");
    }

    private static boolean clientClosed(StompClient client) throws Exception {
        var closed = (AtomicBoolean) fieldValue(client, "closed");
        return closed.get();
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

    @BeforeAll
    static void shortConnectTimeout() {
        System.setProperty("stomp4j.websocket.connectTimeoutSeconds", "1");
    }

    @Test
    @DisplayName("StompClient closes WebSocket transport when connect fails")
    void shouldCleanupWebSocketClientAfterFailedConnect() throws Exception {
        int port;
        try (var unused = new ServerSocket(0)) {
            port = unused.getLocalPort();
        }

        var client = StompClient.create("ws://127.0.0.1:%d".formatted(port));

        assertThatThrownBy(client::connect).isInstanceOf(StompException.class);

        assertThat(clientClosed(client)).isTrue();
        var transport = (WebSocketTransport) fieldValue(client, "transport");
        assertHttpClientClosed(transport);
        client.close();
    }

    @Test
    @DisplayName("WebSocketTransport closes HttpClient when connect fails")
    void shouldCloseHttpClientWhenConnectFails() throws Exception {
        int port;
        try (var unused = new ServerSocket(0)) {
            port = unused.getLocalPort();
        }

        var transport = new WebSocketTransport(URI.create("ws://127.0.0.1:%d".formatted(port)), noopListener());

        assertThatThrownBy(transport::connect).isInstanceOf(StompException.class);
        assertHttpClientClosed(transport);
        assertThat(fieldValue(transport, "webSocketClient")).isNull();

        assertThatThrownBy(transport::connect).isInstanceOf(StompException.class);
        transport.close();
    }
}
