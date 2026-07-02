package dev.vepo.stomp4j.bridge.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import dev.vepo.stomp4j.bridge.KafkaBridgeConfig;
import dev.vepo.stomp4j.bridge.StompKafkaBridge;
import dev.vepo.stomp4j.commons.TransportType;

@Execution(ExecutionMode.SAME_THREAD)
class StompKafkaBridgeStartupFailureTest {

    private static int allocatePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to allocate ephemeral TCP port", ex);
        }
    }

    private static Object fieldValue(StompKafkaBridge bridge, String name) throws Exception {
        Field field = StompKafkaBridge.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(bridge);
    }

    private static void invokeStart(StompKafkaBridge bridge) throws Exception {
        Method start = StompKafkaBridge.class.getDeclaredMethod("start");
        start.setAccessible(true);
        try {
            start.invoke(bridge);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            var cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw ex;
        }
    }

    private static StompKafkaBridge newBridge(KafkaBridgeConfig kafkaConfig, int port) throws Exception {
        var builder = StompKafkaBridge.builder()
                                      .kafkaConfig(kafkaConfig)
                                      .channel(TransportType.TCP, port);
        Constructor<StompKafkaBridge> constructor =
                StompKafkaBridge.class.getDeclaredConstructor(StompKafkaBridge.Builder.class);
        constructor.setAccessible(true);
        return constructor.newInstance(builder);
    }

    private ServerSocketChannel portHolder;

    @Test
    @DisplayName("Builder.start() rolls back producer when STOMP bind fails")
    void builderStartShouldRollbackProducerWhenStompBindFails() throws Exception {
        var port = allocatePort();
        portHolder = ServerSocketChannel.open();
        portHolder.bind(new InetSocketAddress(port));

        var kafkaConfig = KafkaBridgeConfig.builder()
                                           .bootstrapServers("127.0.0.1:9092")
                                           .build();

        assertThatThrownBy(() -> StompKafkaBridge.builder()
                                                 .kafkaConfig(kafkaConfig)
                                                 .channel(TransportType.TCP, port)
                                                 .start())
                                                          .isInstanceOf(IllegalStateException.class)
                                                          .hasMessageContaining(String.valueOf(port));
    }

    @Test
    @DisplayName("StompKafkaBridge closes Kafka producer when STOMP bind fails")
    void shouldCloseProducerWhenStompBindFails() throws Exception {
        var port = allocatePort();
        portHolder = ServerSocketChannel.open();
        portHolder.bind(new InetSocketAddress(port));

        var kafkaConfig = KafkaBridgeConfig.builder()
                                           .bootstrapServers("127.0.0.1:9092")
                                           .build();
        var bridge = newBridge(kafkaConfig, port);

        assertThatThrownBy(() -> invokeStart(bridge))
                                                     .isInstanceOf(IllegalStateException.class)
                                                     .hasMessageContaining(String.valueOf(port));
        assertThat(fieldValue(bridge, "producer")).isNull();
        assertThat(fieldValue(bridge, "server")).isNull();
        assertThat(fieldValue(bridge, "consumerManager")).isNull();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (portHolder != null && portHolder.isOpen()) {
            portHolder.close();
        }
        portHolder = null;
    }
}
