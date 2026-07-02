package dev.vepo.stomp4j.spring.boot.autoconfigure.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.net.ServerSocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.client.exceptions.StompException;
import dev.vepo.stomp4j.spring.boot.autoconfigure.client.StompClientFactory;
import dev.vepo.stomp4j.spring.boot.autoconfigure.client.StompClientLifecycle;
import dev.vepo.stomp4j.spring.boot.autoconfigure.client.StompClientProperties;

@Execution(ExecutionMode.SAME_THREAD)
class StompClientLifecycleTest {

    @Test
    @DisplayName("Failed connect clears client reference and leaves lifecycle stopped")
    void shouldClearClientWhenConnectFails() throws Exception {
        int port;
        try (var unused = new ServerSocket(0)) {
            port = unused.getLocalPort();
        }

        var properties = new StompClientProperties();
        properties.setUrl("stomp://127.0.0.1:%d".formatted(port));
        var lifecycle = new StompClientLifecycle(new StompClientFactory(properties, null));

        assertThatThrownBy(lifecycle::start).isInstanceOf(StompException.class);

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(clientField(lifecycle)).isNull();
        assertThatThrownBy(lifecycle::client).isInstanceOf(IllegalStateException.class);
        assertThatCode(lifecycle::stop).doesNotThrowAnyException();
        assertThat(clientField(lifecycle)).isNull();
    }

    private static Object clientField(StompClientLifecycle lifecycle) throws Exception {
        Field field = StompClientLifecycle.class.getDeclaredField("client");
        field.setAccessible(true);
        return field.get(lifecycle);
    }
}
