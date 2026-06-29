package dev.vepo.stomp4j.quarkus.server.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class StompServerExtensionTest {

    @Test
    void shouldEchoChatMessageToTopic() {
        var received = new AtomicReference<String>();
        try (var client = StompClient.create("stomp://localhost:15620", new UserCredential("demo", "demo"))) {
            client.connect();
            client.subscribe("/topic/chat/lobby", body -> received.set(body));
            client.sendPlain("/app/chat/lobby", "hello-quarkus-server", "text/plain");
            await().atMost(Duration.ofSeconds(15)).until(() -> "hello-quarkus-server".equals(received.get()));
            assertThat(received.get()).isEqualTo("hello-quarkus-server");
        }
    }
}
