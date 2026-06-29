# Stomp4J

A **Java 21** library for the [STOMP messaging protocol](https://stomp.github.io/) — STOMP client, embeddable server, and shared wire-format model. No application framework required.

**License:** Apache 2.0 · **Group:** `dev.vepo` · **Repository:** [github.com/vepo/stomp4j](https://github.com/vepo/stomp4j)

## Why Stomp4J?

Use Stomp4J when you need to:

- Connect a Java application to any STOMP broker (ActiveMQ Artemis, RabbitMQ STOMP plugin, Network Rail feeds, etc.)
- Embed a lightweight STOMP endpoint inside your own process for tests or small services
- Work with STOMP frames in Java without pulling in Spring or Jakarta EE

The library follows the official STOMP specification (versions 1.0, 1.1, and 1.2) and exposes a small, intention-revealing API.

## Quick start

Add the client dependency (replace the version with the [latest release](https://github.com/vepo/stomp4j/releases)):

```xml
<dependency>
    <groupId>dev.vepo</groupId>
    <artifactId>stomp4j-client</artifactId>
    <version>1.1.0</version>
</dependency>
```

Connect, subscribe, and receive messages:

```java
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;

try (var client = StompClient.create(
        "stomp://localhost:61613",
        new UserCredential("user", "pass"))) {

    client.connect();
    client.subscribe("/topic/events", body -> {
        // handle each MESSAGE frame body
    });
    client.join(); // blocks until close()
}
```

For an embedded server, add `stomp4j-server` and see [docs/getting-started.md](docs/getting-started.md#embedded-server).

## Documentation

Read in order of complexity — each level builds on the previous one.

| Level | Document | What you learn |
|-------|----------|----------------|
| **Start here** | [docs/overview.md](docs/overview.md) | Purpose, modules, design philosophy |
| **Reference** | [docs/features.md](docs/features.md) | Supported capabilities (client, server, protocol) |
| **Beginner** | [docs/getting-started.md](docs/getting-started.md) | Maven setup, first client & server |
| **Intermediate** | [docs/client-guide.md](docs/client-guide.md) | Transports, subscriptions, credentials, TLS |
| **Intermediate** | [docs/server-guide.md](docs/server-guide.md) | Handlers, outbound push, authentication |
| **Intermediate** | [docs/spring-guide.md](docs/spring-guide.md) | Spring Boot starters and samples |
| **Intermediate** | [docs/quarkus-guide.md](docs/quarkus-guide.md) | Quarkus extensions and CDI Events |
| **Intermediate** | [docs/kafka-bridge-guide.md](docs/kafka-bridge-guide.md) | STOMP ↔ Kafka bridge (library, JAR, Docker) |
| **Advanced** | [docs/advanced-topics.md](docs/advanced-topics.md) | Protocol versions, SPI, JPMS, wire format |

Full index: [docs/README.md](docs/README.md)

## Modules

| Artifact | Use when |
|----------|----------|
| `stomp4j-client` | Your app talks to an external STOMP broker |
| `stomp4j-server` | You embed a STOMP server (tests, gateways, prototypes) |
| `stomp4j-commons` | You build on STOMP frames directly (usually pulled transitively) |
| `stomp4j-spring-boot-starter-client` | Spring Boot app connecting to an external broker |
| `stomp4j-spring-boot-starter-server` | Spring Boot app embedding a STOMP server |
| `stomp4j-quarkus-client` | Quarkus app connecting to an external broker (CDI Events) |
| `stomp4j-quarkus-server` | Quarkus app embedding a STOMP server |
| `stomp4j-kafka-bridge` | Embed STOMP ↔ Kafka routing in your app |
| `stomp4j-kafka-bridge-runner` | Run the bridge as a standalone process (`java -jar`) |

Details: [docs/overview.md#modules](docs/overview.md#modules)

## Protocol support

| Version | Client | Server |
|---------|--------|--------|
| STOMP 1.2 | Supported | Supported (default) |
| STOMP 1.1 | Supported | Configurable |
| STOMP 1.0 | Supported | Configurable |

Specification: [stomp.github.io](https://stomp.github.io/)

## Requirements

- Java 21+
- SLF4J on the classpath (you provide the binding, e.g. Logback)
- For running this project's tests: Docker (Testcontainers + ActiveMQ Artemis)

## Building from source

```bash
git clone https://github.com/vepo/stomp4j.git
cd stomp4j
mvn verify
```

Optional local broker: `docker compose -f scripts/docker/docker-compose.yaml up`

## Contributing

- Architecture & conventions: [ARCHITECTURE.md](ARCHITECTURE.md)
- Domain language: [docs/domain-specification.md](docs/domain-specification.md)
- Agent / maintainer index: [AGENTS.md](AGENTS.md)
