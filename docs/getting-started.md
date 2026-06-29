# Getting started

Minimal steps to add Stomp4J to a project and exchange messages. For background, read [overview.md](overview.md) first.

## Prerequisites

- **Java 21** or later
- **Maven** or Gradle
- An **SLF4J** implementation on the runtime classpath (the library logs via SLF4J only)

## Maven dependencies

Check [GitHub Releases](https://github.com/vepo/stomp4j/releases) or [Maven Central](https://central.sonatype.com/namespace/dev.vepo) for the latest version.

### Client only

```xml
<dependency>
    <groupId>dev.vepo</groupId>
    <artifactId>stomp4j-client</artifactId>
    <version>1.1.0</version>
</dependency>
```

### Embedded server

```xml
<dependency>
    <groupId>dev.vepo</groupId>
    <artifactId>stomp4j-server</artifactId>
    <version>1.1.0</version>
</dependency>
```

`stomp4j-commons` is pulled in automatically. You rarely need to declare it explicitly.

## First STOMP client

This example connects to a broker, subscribes with a callback, and blocks until the client is closed.

```java
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;

public class HelloStomp {

    public static void main(String[] args) {
        var url = "stomp://localhost:61613";
        var credentials = new UserCredential("admin", "admin");

        try (var client = StompClient.create(url, credentials)) {
            client.connect();
            client.subscribe("/topic/greetings", body -> {
                System.out.println("Received: " + body);
            });
            client.join(); // blocks until close()
        }
    }
}
```

### Local broker (optional)

From the Stomp4J repository:

```bash
docker compose -f scripts/docker/docker-compose.yaml up
```

Default STOMP TCP port: **61613**.

### Lifecycle in short

1. `StompClient.create(url, …)` — factory; no network I/O yet.
2. `connect()` — opens transport, sends `CONNECT`, waits for `CONNECTED`.
3. `subscribe(…)` — sends `SUBSCRIBE`; messages arrive via callback or `poll()`.
4. `sendPlain(destination, body, contentType)` — sends a `SEND` frame.
5. `close()` — `DISCONNECT` and release resources (also called from try-with-resources).

More options: [client-guide.md](client-guide.md).

## Embedded server

A minimal server that accepts any subscription and prints inbound messages:

```java
import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.server.StompServer;

public class HelloServer {

    public static void main(String[] args) {
        try (var server = StompServer.builder()
                .channel(TransportType.TCP, 5500)
                .subscription(topic -> true)
                .handler(message -> {
                    System.out.println(message.destination() + " -> " + message.body());
                })
                .start()) {

            System.out.println("STOMP server listening on stomp://localhost:5500");
            Thread.currentThread().join(); // keep process alive
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

Connect with the client using `stomp://localhost:5500` (no credentials unless you add an authenticator).

Full server patterns: [server-guide.md](server-guide.md).

## Common next steps

| Need | Document |
|------|----------|
| WebSocket URLs, TLS, protocol version | [client-guide.md](client-guide.md) |
| Push messages from server to clients | [server-guide.md](server-guide.md#outbound-messages) |
| Custom transport or STOMP version | [advanced-topics.md](advanced-topics.md) |
