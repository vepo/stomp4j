# Architecture & Conventions

Canonical reference for developers and AI agents working on Stomp4J. For STOMP protocol terms see [docs/domain-specification.md](docs/domain-specification.md).

**Last updated:** 2026-06-29

## 1. Project overview

Stomp4J is a **Java 21 library** (not a runnable application) that implements the [STOMP protocol](https://stomp.github.io/) for clients and an embeddable server.

| Aspect | Detail |
|--------|--------|
| Build | Apache Maven multi-module parent (`stomp4j-parent`) |
| Group / version | `dev.vepo` / `1.1.1-SNAPSHOT` |
| Module system | JPMS — every module has `module-info.java` |
| License | Apache 2.0 |
| Logging | SLF4J (consumer provides implementation) |

Published artifacts: `stomp4j-commons`, `stomp4j-client`, `stomp4j-server`, `stomp4j-kafka-bridge`, `stomp4j-kafka-bridge-runner`, `stomp4j-spring-boot-autoconfigure`, `stomp4j-spring-boot-starter-client`, `stomp4j-spring-boot-starter-server`, `stomp4j-quarkus-cdi`, `stomp4j-quarkus-client`, `stomp4j-quarkus-client-deployment`, `stomp4j-quarkus-server`, `stomp4j-quarkus-server-deployment`.

## 2. Module dependency graph

```
stomp4j-parent
    ├── stomp4j-commons     (no internal deps)
    ├── stomp4j-client      → commons
    ├── stomp4j-server      → commons, vertx-web
    │                         (test → client)
    └── stomp4j-spring      → optional Spring Boot layer
        ├── stomp4j-spring-boot-autoconfigure → client, server (optional)
        ├── stomp4j-spring-boot-starter-client
        └── stomp4j-spring-boot-starter-server
    └── stomp4j-quarkus     → optional Quarkus 3.17 layer (no JPMS)
        ├── stomp4j-quarkus-cdi               → shared @StompSync / @StompAsync
        ├── stomp4j-quarkus-client            → runtime extension
        ├── stomp4j-quarkus-client-deployment → build-time processor
        ├── stomp4j-quarkus-client-integration-tests
        ├── stomp4j-quarkus-server
        ├── stomp4j-quarkus-server-deployment
        └── stomp4j-quarkus-server-integration-tests
    └── stomp4j-bridge      → optional Kafka bridge layer
        ├── stomp4j-kafka-bridge        → server, kafka-clients
        └── stomp4j-kafka-bridge-runner → kafka-bridge (executable)
```

Dependencies flow **downward only**: `client` and `server` depend on `commons`; `server` tests may use `client` but production `server` does not depend on `client`. `stomp4j-kafka-bridge` depends on `stomp4j-server` only (not `client`) in production; tests use `stomp4j-client`. Quarkus extensions depend on `client` or `server` respectively, not on each other.

## 3. Module responsibilities

### 3.1 `stomp4j-commons`

Shared STOMP wire-format model.

| Package | Responsibility |
|---------|----------------|
| `dev.vepo.stomp4j.commons` | `TransportType` (`TCP`, `WEB_SOCKET`) |
| `dev.vepo.stomp4j.commons.protocol` | `Command`, `Header`, `Headers`, `Message`, `MessageBuilder`, `MessageBuffer` |

`Message` is a record with `encode()` / `readMessage()` for STOMP framing. `MessageBuffer` accumulates stream bytes until the NUL (`\u0000`) terminator.

**Exports:** `commons`, `commons.protocol`

### 3.2 `stomp4j-client`

Full STOMP client — versions 1.0, 1.1, 1.2 over TCP and WebSocket.

| Package | Visibility | Responsibility |
|---------|-----------|----------------|
| `dev.vepo.stomp4j.client` | **Public** | `StompClient`, `Subscription`, `UserCredential` |
| `dev.vepo.stomp4j.client.protocol` | Public | Abstract `Stomp` + version negotiation |
| `dev.vepo.stomp4j.client.protocol.v1_0/v1_1/v1_2` | Public | Version-specific subscribe/send/ack/unsubscribe |
| `dev.vepo.stomp4j.client.transport` | Public | `Transport`, `TransportProvider`, `TransportListener` |
| `dev.vepo.stomp4j.client.internal` | **Internal** | `StompClientImpl` |
| `dev.vepo.stomp4j.client.internal.transport` | Internal | `TcpTransport`, `WebSocketTransport`, `TransportFactory`, providers |

**Client lifecycle:**

```
StompClient.create(url, credentials?, transportType?, protocols?)
    → StompClientImpl
        → TransportFactory.create(uri)     [SPI: scheme → provider]
        → transport.connect()
        → onConnected → Stomp CONNECT frame
        → CONNECTED → select protocol via ServiceLoader
        → subscribe / send / heartbeat
```

**URL schemes:**

| Scheme | Transport | Provider |
|--------|-----------|----------|
| `stomp://host:port` | TCP | `TcpTransportProvider` |
| `stomps://host:port` | TLS TCP | `SecureTcpTransportProvider` |
| `ws://host:port/path` | WebSocket | `WebSocketTransportProvider` |
| `wss://host:port/path` | TLS WebSocket | `SecureWebSocketTransportProvider` |

**Subscription modes:**

1. **Callback** — `subscribe(topic, Consumer<String>)`
2. **Polling** — `subscribe(topic)` → `hasData()` / `poll()`

### 3.3 `stomp4j-server`

Embedded STOMP server (actively developed; thinner than client).

| Package | Visibility | Responsibility |
|---------|-----------|----------------|
| `dev.vepo.stomp4j.server` | **Public** | `StompServer`, `StompMessage`, `StompSession`, `MessageHandler`, `SubscriptionHandler`, `OutboundChannel`, `TransportChannel` |
| `dev.vepo.stomp4j.server.auth` | Public | `StompAuthenticator`, `Credentials` |
| `dev.vepo.stomp4j.server.channels` | Internal | `Channel`, `TcpChannel`, `WebSocketChannel`, `BufferPool`, `ChannelListener` |
| `dev.vepo.stomp4j.server.session` | Internal | Per-connection `Session` state machine |

**Server lifecycle:**

```
StompServer.builder()
    .channel(TCP, port) / .channel(WEB_SOCKET, port)
    .supportedVersions("1.2", "1.1", "1.0")
    .heartbeat(Duration.ofSeconds(30))
    .serverName("stomp4j")
    .ssl(SSLContext) / .ssl(SSLContext, keyStorePath, password)   // optional TLS
    .handler(MessageHandler)
    .subscription(SubscriptionHandler)
    .authenticator(StompAuthenticator)   // optional; enforced on CONNECT
    .connectionListener(StompConnectionListener)
    .start()
        → Channel.load(type, port) → TcpChannel | WebSocketChannel
        → Session per connection
        → CONNECT → auth + version negotiation → CONNECTED
        → SUBSCRIBE / UNSUBSCRIBE / SEND / ACK / NACK / DISCONNECT
        → SEND inbound → MessageHandler.onSend(StompMessage)
        → outboundChannel().send() → broadcast to subscribed sessions
```

### 3.4 Quarkus extensions (`stomp4j-quarkus-*`)

Optional Quarkus 3.17 layer using CDI Events (no `*Template` APIs). No `module-info.java` — classpath + Jandex index on runtime modules.

| Module | Role |
|--------|------|
| `stomp4j-quarkus-cdi` | Shared qualifiers `@StompSync`, `@StompAsync` |
| `stomp4j-quarkus-client` | Client runtime: `StompSession`, outbound/inbound events, `@StompDestination` |
| `stomp4j-quarkus-client-deployment` | Build-time inbound observer scan, `ConfigMapping`, `AdditionalBeanBuildItem` |
| `stomp4j-quarkus-server` | Server runtime: `StompServer` producer, outbound observers |
| `stomp4j-quarkus-server-deployment` | Server `ConfigMapping`, bean registration |
| `*-integration-tests` | `@QuarkusTest` with Testcontainers (Artemis) or embedded server |

User guide: [docs/quarkus-guide.md](docs/quarkus-guide.md).

### 3.5 Kafka bridge (`stomp4j-bridge`)

Optional STOMP ↔ Kafka routing. Depends on `stomp4j-server` + `kafka-clients` only in production.

| Module | Role |
|--------|------|
| `stomp4j-kafka-bridge` | `StompKafkaBridge`, `DestinationMapping`, `KafkaBridgeConfig` |
| `stomp4j-kafka-bridge-runner` | `StompKafkaBridgeApplication` fat JAR |

User guide: [docs/kafka-bridge-guide.md](docs/kafka-bridge-guide.md).

## 4. Public API entry points

There is no `main()` in core modules. Consumers use:

### Client

```java
try (var client = StompClient.create(url, credentials)) {
    client.connect();
    client.subscribe("/topic/foo", data -> { /* ... */ });
    client.join();          // blocks until close
    client.sendPlain(dest, content, contentType);
    client.unsubscribe(topic);
}
```

### Server

```java
try (var server = StompServer.builder()
        .channel(TransportType.TCP, 5500)
        .channel(TransportType.WEB_SOCKET, 5501)
        .handler(message -> {
            // message.sessionChannel() replies to this connection only
            // server.outboundChannel() broadcasts to all subscribed sessions
        })
        .subscription(topic -> true)
        .authenticator(credentials -> true)
        .start()) {

    server.outboundChannel().send(message);
}
```

## 5. Extension points (SPI)

| Feature | How to add |
|---------|-----------|
| New transport (e.g. `wss://`) | Implement `TransportProvider`; register in `module-info.java` `provides` + `META-INF/services` |
| New STOMP version | Extend `Stomp`; register in SPI |
| Server auth | Implemented — `StompAuthenticator` on `CONNECT` |
| Server protocol versions | Implemented — `supportedVersions()` on builder |
| New STOMP commands | `Command` enum + client `Stomp1_x` + server `Session` |
| Client TLS | `stomps://` / `wss://` via SPI; optional `StompClient.create(url, SSLContext)` |
| Server TLS | `StompServer.builder().ssl(...)` |

SPI registrations live in `client/src/main/resources/META-INF/services/`.

## 6. Design patterns

| Pattern | Where |
|---------|-------|
| Factory / static create | `StompClient.create()`, `TransportFactory.create()`, `Channel.load()`, `MessageBuilder.builder()` |
| Builder | `StompServer.Builder`, `Headers.builder()` |
| Strategy | Version-specific `Stomp1_x` subclasses |
| SPI (ServiceLoader) | `TransportProvider`, `Stomp` protocol versions |
| Listener / callback | `TransportListener`, `ChannelListener`, `MessageHandler`, `SubscriptionHandler` |
| State machine | `Session.Status` (`STARTED` → `CONNECTED`) |
| Object pool | `BufferPool` for NIO read buffers |
| Facade | `StompClient` hides `StompClientImpl` |

## 7. Technology choices

| Concern | Choice |
|---------|--------|
| Client WebSocket | `java.net.http` (JDK) |
| Server TCP | `java.nio` (NIO) |
| Server WebSocket | Vert.x 5 (`vertx-web`) |
| WebSocket handshake key | `commons-codec` (`ClientKey`) |
| No Spring, no Quarkus | Plain Java library |

## 8. Testing

| Tool | Purpose |
|------|---------|
| JUnit 5 (6.0.1) | All tests; parameterized for protocol versions |
| AssertJ (3.27.6) | Assertions |
| Awaitility (4.3.0) | Async polling |
| Testcontainers (2.0.2) | `testcontainers-activemq` — Artemis Docker image |
| ActiveMQ client (6.2.0) | JMS side of integration tests |
| Logback (1.5.21) | Test-scoped logging |
| JaCoCo (0.8.12) | Coverage → SonarCloud |

### Test layout

| Module | Tests | Strategy |
|--------|-------|----------|
| `commons` | `MessageBufferTest` | Unit — protocol framing |
| `client` | `StompClientTest` | Unit — URL validation |
| `client` | `StompClientTcpTest`, `StompClientWebSocketTest` | Integration vs ActiveMQ (all STOMP versions) |
| `server` | `StompServerTest` | Integration — embedded server + `stomp4j-client` |

`StompContainer` JUnit 5 extension starts a shared `StompActiveMqContainer` (one broker per JVM, stopped on shutdown) and injects URLs/credentials into test methods. Docker-backed tests use `@Tag("integration")`.

**Run:** `mvn verify` from repo root (full suite, Docker required). **Fast loop:** `mvn -Pfast -pl commons,client,server test` skips integration-tagged tests. Integration tests require **Docker** (Testcontainers).

**Local broker (optional):** `docker compose -f scripts/docker/docker-compose.yaml up`

## 9. CI/CD

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `maven.yml` | Push (all branches) | `mvn verify jacoco:report` + SonarCloud |
| `prepare-release.yml` | Manual on `main` | `mvn verify`, release version bump, `v*` tag, next snapshot |
| `release.yml` | Push `v*` tag | GPG sign, deploy to Maven Central, GitHub Release |

SonarCloud: org `vepo-github`, project `vepo_stomp4j`.

### Release process

1. Merge changes to `main` and wait for CI to pass.
2. Run **Prepare to release** (`prepare-release.yml`) on `main`.
3. **Release** (`release.yml`) runs automatically when the `v*` tag is pushed.
4. Verify artifacts on [Maven Central](https://central.sonatype.com/) (namespace `dev.vepo`).

**Secrets:** `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `OSSRH_USERNAME`, `OSSRH_TOKEN` (release workflow only).

**Branch protection:** if `main` requires pull requests, grant the GitHub Actions bot permission to push commits and tags, or provide a `RELEASE_PAT` secret with `contents: write` and use it for the prepare workflow push step.

## 10. Naming conventions

| Convention | Examples |
|------------|---------|
| Base package | `dev.vepo.stomp4j` |
| JPMS module names | `stomp4j.commons`, `stomp4j.client`, `stomp4j.server` |
| Version classes | `Stomp1_0`, `Stomp1_1`, `Stomp1_2` |
| Test packages | `*.tests`, `*.tests.infra` |
| Internal impl | `*Impl` suffix; `internal` package (not exported) |
| Records | `Message`, `UserCredential`, `Credentials`, `TransportChannel` |

## 11. Module boundaries (JPMS)

```
PUBLIC (exported):
  client:  StompClient, Subscription, UserCredential, protocol.*, transport interfaces
  server:  StompServer, MessageHandler, SubscriptionHandler, OutboundChannel, auth.*
  commons: TransportType, protocol.*

INTERNAL (do not use from outside module):
  client.internal.*
  server.channels.*, server.session.*
```

When adding public API, update `module-info.java` `exports`. When adding SPI, update `provides` and `META-INF/services`.

## 12. Known gaps / WIP

- Server: no durable message store or queue semantics (embeddable pub/sub only)
- Kafka bridge: no STOMP transactions; client-ack ↔ Kafka offset not supported

Update this section when closing gaps.

## 13. Feature workflow (agents)

When adding or changing behaviour:

1. Read [docs/domain-specification.md](docs/domain-specification.md) — STOMP terms and invariants.
2. Place code in the correct module and package (§3, §11).
3. Update SPI / `module-info.java` if adding transports or protocol versions.
4. Add or extend tests in the same module (`commons` unit, `client`/`server` integration).
5. Update [docs/features.md](docs/features.md) when user-visible capabilities change; keep §12 in sync for known gaps.
6. Update this document if architecture, boundaries, or WIP status changes.
7. Run `mvn verify` before finishing.

## 14. Useful commands

```bash
# Full build + test + coverage
mvn verify

# Single module
mvn -pl client test
mvn -pl server test
mvn -pl commons test

# Local Artemis broker
docker compose -f scripts/docker/docker-compose.yaml up
```

## 15. Related docs

| Document | Purpose |
|----------|---------|
| [README.md](README.md) | Project overview and quick start |
| [docs/README.md](docs/README.md) | User documentation index |
| [docs/features.md](docs/features.md) | Supported capabilities checklist |
| [docs/overview.md](docs/overview.md) | Purpose, modules, design philosophy |
| [docs/getting-started.md](docs/getting-started.md) | Maven setup, first client & server |
| [docs/client-guide.md](docs/client-guide.md) | Client API in depth |
| [docs/server-guide.md](docs/server-guide.md) | Embedded server API in depth |
| [docs/advanced-topics.md](docs/advanced-topics.md) | SPI, JPMS, TLS, wire format |
| [docs/domain-specification.md](docs/domain-specification.md) | STOMP ubiquitous language |
| [resources/roteiros/](resources/roteiros/) | Design rationale (Portuguese) |
| [AGENTS.md](AGENTS.md) | Agent index and rule map |
| [.cursor/rules/](.cursor/rules/) | Cursor rules for AI agents |
