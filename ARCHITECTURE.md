# Architecture & Conventions

Canonical reference for developers and AI agents working on Stomp4J. For STOMP protocol terms see [docs/domain-specification.md](docs/domain-specification.md).

**Last updated:** 2026-06-29 (threading model §4)

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
| `dev.vepo.stomp4j.commons.nio` | Internal NIO helpers: `TcpOutboundQueue`, `SslEngineIo`, `SelectionKeys`, `NioBuffers` (qualified export to `client` and `server` only) |

`Message` is a record with `encode()` / `readMessage()` for STOMP framing. `MessageBuffer` accumulates stream bytes until the NUL (`\u0000`) terminator.

**Exports:** `commons`, `commons.protocol`; `commons.nio` → `stomp4j.client`, `stomp4j.server`

### 3.2 `stomp4j-client`

Full STOMP client — versions 1.0, 1.1, 1.2 over TCP and WebSocket.

| Package | Visibility | Responsibility |
|---------|-----------|----------------|
| `dev.vepo.stomp4j.client` | **Public** | `StompClient`, `Subscription`, `UserCredential` |
| `dev.vepo.stomp4j.client.protocol` | Public | Abstract `Stomp` + version negotiation |
| `dev.vepo.stomp4j.client.protocol.v1_0/v1_1/v1_2` | Public | Version-specific subscribe/send/ack/unsubscribe |
| `dev.vepo.stomp4j.client.transport` | Public | `Transport`, `TransportProvider`, `TransportListener` |
| `dev.vepo.stomp4j.client.internal` | **Internal** | `StompClientImpl` |
| `dev.vepo.stomp4j.client.internal.transport` | Internal | `NioTcpTransport`, `NioSecureTcpTransport`, `WebSocketTransport`, `TransportFactory`, providers |

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

## 4. Threading and concurrency

**Design intent:** `StompClient` instances must be safe to use from multiple application threads (connect once, concurrent `send`, subscribe/unsubscribe while the transport delivers `MESSAGE` frames). The server processes each TCP/WebSocket connection on a dedicated I/O path; application handlers must not assume a particular caller thread beyond “not the caller’s business thread” for blocking work.

### 4.1 Client — thread roles

| Thread | Source | Calls into |
|--------|--------|------------|
| **Application** | Test or app code | `connect()`, `subscribe()`, `send()`, `close()`, `join()`, polling `hasData()` / `poll()` |
| **Transport read** | `NioTcpTransport` / `NioSecureTcpTransport` selector I/O thread; `java.net.http` WebSocket callbacks | `TransportListener.onMessage()` → `StompClientImpl` frame dispatch |
| **Heartbeat** | `StompClientImpl` single-thread `ScheduledExecutorService` | `transport.send(HEARTBEAT)` |

**Inbound path:** transport read thread → `ConnectionListener.onMessage()` → `consumeMessage()` → user `Consumer` callback **or** enqueue on `ConcurrentLinkedQueue` for polling mode.

**Outbound path:** application (or heartbeat) thread → `Stomp` protocol → `transport.send()` → per-connection outbound queue under `sendLock` (TCP/TLS) or synchronized WebSocket send; the transport I/O thread drains the queue and reads inbound data on the same selector loop (TCP/TLS).

```
Application thread(s)          Transport read thread
        |                                |
   connect / subscribe / send             |
        |                                |
        v                                v
   StompClientImpl  <----- onMessage --- Transport
        |
        +-- callback Consumer (on read thread)
        +-- poll queue (ConcurrentLinkedQueue per subscription)
```

### 4.2 Client — synchronization today

| State | Mechanism | Notes |
|-------|-----------|-------|
| `pendingReceipts` | `ConcurrentHashMap` | Receipt futures safe across threads |
| Polling queues | `Collections.synchronizedMap` + `ConcurrentLinkedQueue` | `hasData()` / `poll()` vs inbound add |
| `closed` | `AtomicBoolean` | Idempotent `close()` |
| `selectedProtocol`, `connectionError` | `AtomicReference` | Published after `CONNECTED` |
| `consumers`, `deliveryConsumers` | Plain `HashMap` | **Not thread-safe** — concurrent subscribe/unsubscribe vs inbound dispatch can race |
| `subscribe()` / `unsubscribe()` | No global lock | Concurrent API calls from multiple threads are not serialised |
| Transport outbound (TCP/TLS) | `sendLock` + `TcpOutboundQueue` (`commons.nio`) | Concurrent `send()` serialised; I/O thread drains queue without corrupting frames |
| `join()` | `synchronized(lock)` + `wait()` | Woken from `close()` |

**Callback threading:** `subscribe(topic, Consumer<…>)` invokes the consumer on the **transport read thread**. Callers must not perform blocking work or touch non-thread-safe test state without synchronisation. Prefer polling mode or hand off to an application executor inside the callback.

### 4.3 Client — target contract (thread-safe)

| Operation | Expected safety |
|-----------|-----------------|
| `connect()` | Once; happens-before any successful `send` / `subscribe` |
| `send()` / `sendPlain()` | Callable concurrently after connected |
| `subscribe()` / `unsubscribe()` | Callable concurrently; must not lose or duplicate dispatch |
| `close()` | Idempotent; happens-before `join()` returns |
| Callback mode | User callback may run on transport thread; library documents this; optional executor dispatch is a future enhancement |
| Polling mode | `hasData()` / `poll()` safe vs concurrent inbound messages |

### 4.4 Server — thread roles

| Component | Thread model |
|-----------|--------------|
| `TcpChannel` | NIO `Selector` loop on one dedicated I/O thread; per-session outbound via `TcpOutboundQueue` + `ioLock` (handler/broadcast threads enqueue; I/O thread drains); TLS via `SslEngineIo` (`commons.nio`) |
| `WebSocketChannel` | Vert.x 5 event loop |
| `Session` | Frame handling on the channel I/O thread for that connection; `HashMap` / `HashSet` fields assume **single-threaded access per session** |
| `MessageHandler` / `SubscriptionHandler` | Invoked on the session I/O thread — must return quickly; offload blocking work |
| `StompServer.start()` / `close()` | `synchronized` on server instance |
| Heartbeat | Shared `ScheduledExecutorService` per server |

Broadcast via `outboundChannel().send()` fans out to all sessions from the caller’s thread.

### 4.5 Tests — parallelism and flakiness

| Module | `junit-platform.properties` | Typical class annotation |
|--------|----------------------------|---------------------------|
| `client` | `parallel.enabled=false` | `@Execution(SAME_THREAD)` on broker integration tests |
| `server` | `parallel.enabled=true`, concurrent classes/methods | `@Execution(CONCURRENT)` on embedded-server tests |

Server parallel safety relies on **exclusive destinations** (`TestDestinations`), **ephemeral ports** (`EmbeddedServerFixture` / `EphemeralPorts`), and **one client per scenario** — not on a shared broker per JVM (client uses shared `StompContainer`).

**Common test threading pitfalls:**

1. **Callback vs assertion thread** — asserting in the test thread while the client delivers on the transport thread without `CountDownLatch`, `AtomicReference`, or Awaitility.
2. **`Thread.sleep` races** — e.g. delayed `send` in a background executor while the main thread polls JMS (prefer `StompTestSupport` + Awaitility with `atMost`).
3. **Shared mutable collections** — `ArrayList` updated from `subscribe` callback without synchronisation.
4. **Mismatched parallelism** — server tests run concurrent methods against code paths that assume single-threaded client use; client maps are not yet fully concurrent.
5. **`@Timeout(SEPARATE_THREAD)`** — can hide deadlocks; use only when isolation is required (e.g. TLS handshake).

### 4.6 Improvements (roadmap)

**Client (production) — priority**

1. **Serialise session mutations** — one lock or single-thread executor for `subscribe` / `unsubscribe` / `send` / inbound `consumeMessage` so `HashMap` consumers and protocol state are consistent.
2. **Replace non-concurrent maps** — `ConcurrentHashMap` for `consumers` and `deliveryConsumers`, or confine all access to the serial executor.
3. **Document callback thread** in `StompClient` Javadoc and [client-guide.md](docs/client-guide.md); add optional `Executor` on `SubscribeOptions` for user dispatch.
4. **Happens-before `connect()`** — ensure `connectedLatch` and published `selectedProtocol` visibility for threads that start sending immediately after `connect()` returns.
5. **Transport `send` vs read** — **done (NIO-009):** client TCP/TLS transports serialise outbound data with `sendLock` and a per-connection queue; server `TcpChannel` uses `ioLock` + `TcpOutboundQueue`. Inbound reads run only on the transport I/O thread.

**Tests — priority**

1. **Stabilise client integration tests** on callback threading — use `CountDownLatch` / Awaitility patterns from `StompTestSupport`; remove `Thread.sleep` from `sendMessageTest`.
2. **Concurrent client stress test** — **done (NIO-009):** `NioTcpTransportConcurrentSendTest`, `StompClientConcurrentSendTest`, `TcpChannelConcurrentOutboundTest`.
3. **Revisit server `parallel.enabled`** — keep concurrent execution only where fixtures prove isolation; otherwise default embedded tests to `SAME_THREAD` until client thread-safety is complete.
4. **Align ARCHITECTURE test docs** with actual `junit-platform.properties` per module (see §9).
5. **Handler contract tests** — document that server handlers run on I/O thread; integration tests must not block in handlers.

Track closure of client thread-safety gaps in §13 (Known gaps).

## 5. Public API entry points

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

## 6. Extension points (SPI)

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

## 7. Design patterns

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

## 8. Technology choices

| Concern | Choice |
|---------|--------|
| Client WebSocket | `java.net.http` (JDK) |
| Server TCP | `java.nio` (NIO) |
| Server WebSocket | Vert.x 5 (`vertx-web`) |
| WebSocket handshake key | `commons-codec` (`ClientKey`) |
| No Spring, no Quarkus | Plain Java library |

## 9. Testing

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

`StompContainer` JUnit 5 extension starts a shared `StompActiveMqContainer` (one broker per JVM, stopped on shutdown) and injects URLs/credentials into test methods. Client test infra in `client.tests.infra`: `TestDestinations` (exclusive names), `ArtemisJmsFixture` (per-test JMS), `StompTestSupport` (Awaitility with mandatory `atMost`). Server test infra in `server.tests.infra`: `EphemeralPorts`, `EmbeddedServerFixture` (embedded server on free ports), `TestDestinations`, `ServerTestSupport`.

**Parallel execution:** `client/src/test/resources/junit-platform.properties` sets `parallel.enabled=false`; broker integration tests use `@Execution(SAME_THREAD)`. `server` enables JUnit parallel mode (`parallel.enabled=true`, concurrent classes and methods); embedded tests use `@Execution(CONCURRENT)` with exclusive destinations and ephemeral ports (see §4.5). Docker-backed client tests use `@Tag("integration")`.

**Run:** `mvn verify` from repo root (full suite, Docker required). **During development:** tiered module tests — see `.cursor/rules/stomp4j-testing.mdc`. **Fast loop:** `mvn -Pfast -pl commons,client,server test` skips integration-tagged tests.

### Change impact map (minimum tests)

| Changed area | Run before continuing |
|--------------|------------------------|
| `commons.protocol` | `commons` → `client` + `server` (full module test if framing/commands changed) |
| `client` transport / protocol | `mvn -pl client test` |
| `server.session` / channels | `mvn -pl server test` |
| `bridge/` | `mvn -pl bridge/stomp4j-kafka-bridge test` |
| Test infra (`broker.xml`, `StompContainer`) | All dependent modules — at least `client` |

Integration tests require **Docker** (Testcontainers).

**Local broker (optional):** `docker compose -f scripts/docker/docker-compose.yaml up`

## 10. CI/CD

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

## 11. Naming conventions

| Convention | Examples |
|------------|---------|
| Base package | `dev.vepo.stomp4j` |
| JPMS module names | `stomp4j.commons`, `stomp4j.client`, `stomp4j.server` |
| Version classes | `Stomp1_0`, `Stomp1_1`, `Stomp1_2` |
| Test packages | `*.tests`, `*.tests.infra` |
| Internal impl | `*Impl` suffix; `internal` package (not exported) |
| Records | `Message`, `UserCredential`, `Credentials`, `TransportChannel` |

## 12. Module boundaries (JPMS)

```
PUBLIC (exported):
  client:  StompClient, Subscription, UserCredential, protocol.*, transport interfaces
  server:  StompServer, MessageHandler, SubscriptionHandler, OutboundChannel, auth.*
  commons: TransportType, protocol.*; commons.nio (qualified to client, server)

INTERNAL (do not use from outside module):
  client.internal.*
  server.channels.*, server.session.*
```

When adding public API, update `module-info.java` `exports`. When adding SPI, update `provides` and `META-INF/services`.

## 13. Known gaps / WIP

- Server: no durable message store or queue semantics (embeddable pub/sub only)
- Kafka bridge: no STOMP transactions; client-ack ↔ Kafka offset not supported
- **Client thread safety:** Transport outbound (`send` vs read) is serialised on TCP/TLS (NIO-009). `consumers` / `deliveryConsumers` maps and concurrent `subscribe` / `unsubscribe` vs inbound dispatch are not fully serialised — see §4.2–§4.3.

Update this section when closing gaps.

## 14. Feature workflow (agents)

When adding or changing behaviour:

1. Read [docs/domain-specification.md](docs/domain-specification.md) — STOMP terms and invariants.
2. Place code in the correct module and package (§3, §12).
3. Update SPI / `module-info.java` if adding transports or protocol versions.
4. Add or extend tests in the same module (`commons` unit, `client`/`server` integration); respect threading rules in §4.5.
5. Update [docs/features.md](docs/features.md) when user-visible capabilities change; keep §13 in sync for known gaps.
6. Update this document if architecture, boundaries, threading, or WIP status changes.
7. Run `mvn verify` before finishing.

## 15. Useful commands

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

## 16. Related docs

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
