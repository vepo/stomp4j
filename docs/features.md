# Features

Canonical checklist of what Stomp4J supports today. Use this page to compare capabilities before adopting the library or planning an integration.

When you add, change, or remove user-visible behaviour, **update this page** in the same change — see [documentation.mdc](../.cursor/rules/documentation.mdc).

## At a glance

| Area | Highlights |
|------|------------|
| **Client** | TCP & WebSocket, TLS, STOMP 1.0–1.2, subscribe/send, callback & polling |
| **Server** | Embeddable TCP & WebSocket, handlers, auth, outbound push, TLS |
| **Commons** | Frame encode/decode, headers, commands |
| **Platform** | Java 21, JPMS modules, SLF4J, optional Spring Boot starters, optional Quarkus extensions, Kafka bridge |

## Client (`stomp4j-client`)

| Feature | Status | Details |
|---------|--------|---------|
| STOMP 1.0 | Supported | [advanced-topics.md#protocol-versions](advanced-topics.md#protocol-versions) |
| STOMP 1.1 | Supported | Heart-beat negotiation, subscription `id`, `NACK` |
| STOMP 1.2 | Supported | Default when broker offers it |
| TCP transport (`stomp://`, `stomps://`) | Supported | [client-guide.md#url-schemes-and-transports](client-guide.md#url-schemes-and-transports) |
| WebSocket transport (`ws://`, `wss://`) | Supported | JDK `java.net.http` client |
| TLS (`stomps://`, `wss://`) | Supported | Pass `SSLContext` to `StompClient.create` — [advanced-topics.md#tls](advanced-topics.md#tls) |
| `CONNECT` with login / passcode | Supported | `UserCredential` — [client-guide.md#creating-a-client](client-guide.md#creating-a-client) |
| `SUBSCRIBE` / `UNSUBSCRIBE` | Supported | Callback or polling — [client-guide.md#subscriptions](client-guide.md#subscriptions) |
| `SEND` | Supported | `sendPlain(...)` and `send(..., SendOptions)` with optional receipt |
| Manual consumer `ACK` / `NACK` | Supported | `subscribe(topic, AckMode, Consumer<StompDelivery>)` |
| Producer `SEND` receipt | Supported | `SendOptions.receipt(true)` → `StompReceipt` |
| Client-side auto `ACK` on `MESSAGE` | Supported | Legacy `subscribe` callbacks and `AckMode.AUTO` |
| Heart-beats | Supported | Sent on `CONNECT` when version supports them (1.1, 1.2) |
| `DISCONNECT` on close | Supported | `AutoCloseable` / try-with-resources |
| Restrict offered protocol versions | Supported | `Set<Stomp>` on `create` |
| Force transport type | Supported | `TransportType` on `create` |
| Custom transport (SPI) | Supported | `TransportProvider` — [advanced-topics.md#extending-transports](advanced-topics.md#extending-transports) |
| Custom protocol version (SPI) | Supported | Subclass `Stomp` — [advanced-topics.md#extending-protocol-versions](advanced-topics.md#extending-protocol-versions) |
| `ERROR` frame / failed `CONNECT` | Supported | Throws `StompException` |
| All transport failures as `StompException` | Partial | Not every low-level transport error is wrapped yet |

## Server (`stomp4j-server`)

| Feature | Status | Details |
|---------|--------|---------|
| STOMP 1.0 | Supported | Configure via `.supportedVersions(...)` |
| STOMP 1.1 | Supported | Default set includes 1.1 |
| STOMP 1.2 | Supported | Default negotiated version |
| TCP channel | Supported | `TransportType.TCP` — [server-guide.md#transports](server-guide.md#transports) |
| WebSocket channel | Supported | Vert.x-based; `TransportType.WEB_SOCKET` |
| TLS on TCP and WebSocket | Supported | `.ssl(SSLContext)` or keystore path — [advanced-topics.md#tls](advanced-topics.md#tls) |
| `CONNECT` → `CONNECTED` | Supported | Version negotiation, `server` header |
| `SUBSCRIBE` authorisation | Supported | `SubscriptionHandler` — [server-guide.md#subscriptionhandler--authorise-subscribe](server-guide.md#subscriptionhandler--authorise-subscribe) |
| Inbound `SEND` handling | Supported | `MessageHandler` — [server-guide.md#messagehandler--inbound-send](server-guide.md#messagehandler--inbound-send) |
| `CONNECT` authentication | Supported | Optional `StompAuthenticator` |
| Outbound broadcast | Supported | `StompServer.outboundChannel()` |
| Subscriber ACK/NACK callbacks | Supported | `AcknowledgedOutboundChannel.send(..., SubscriberAckListener)` |
| Per-session reply | Supported | `StompMessage.sessionChannel()` |
| Connection lifecycle hooks | Supported | `StompConnectionListener` |
| Subscription lifecycle hooks | Supported | `SubscriptionHandler.onSubscribed` / `onUnsubscribed` |
| Heart-beat negotiation | Supported | `.heartbeat(Duration)` on builder |
| Configurable server name | Supported | `.serverName(...)` on `CONNECTED` |
| STOMP transactions (`BEGIN` / `COMMIT` / `ABORT`) | Not supported | — |
| Durable queues / message store | Not supported | Embeddable pub/sub only; not a full broker |

## Commons (`stomp4j-commons`)

| Feature | Status | Details |
|---------|--------|---------|
| Frame structure (command, headers, body, NUL) | Supported | [advanced-topics.md#wire-format](advanced-topics.md#wire-format) |
| `Message` encode / decode | Supported | `Message.encode()`, `MessageBuffer` |
| `Command`, `Header`, `Headers` model | Supported | Spec-aligned header names |
| `MessageBuilder` | Supported | Build outbound frames for server or tooling |

## Platform and dependencies

| Feature | Status | Notes |
|---------|--------|-------|
| Java 21+ | Required | Records, modules, modern APIs |
| JPMS modules | Supported | Each artifact has `module-info.java` — [advanced-topics.md#jpms-java-modules](advanced-topics.md#jpms-java-modules) |
| SLF4J logging | Supported | You provide the binding (e.g. Logback) |
| Spring Boot client starter | Supported | [spring-guide.md](spring-guide.md) — `@StompListener`, `StompClientTemplate` |
| Spring Boot server starter | Supported | [spring-guide.md](spring-guide.md) — `StompInboundHandler`, `StompOutboundTemplate` |
| Quarkus client extension | Supported | [quarkus-guide.md](quarkus-guide.md) — CDI Events, `@StompDestination`, `@StompSync` / `@StompAsync` |
| Quarkus server extension | Supported | [quarkus-guide.md](quarkus-guide.md) — `StompInboundHandler`, outbound CDI Events |
| Spring / Quarkus / Jakarta EE | Not required | Plain Java library; Spring Boot and Quarkus are optional |
| Maven artifacts on Maven Central | Supported | `stomp4j-client`, `stomp4j-server`, `stomp4j-commons`, Spring starters, Quarkus extensions |

## STOMP commands (summary)

| Command | Client | Server |
|---------|--------|--------|
| `CONNECT` / `CONNECTED` | Sends / receives | Accepts / responds |
| `SUBSCRIBE` / `UNSUBSCRIBE` | Sends | Accepts (with policy) |
| `SEND` | Sends | Receives via handler |
| `MESSAGE` | Receives | Sends (outbound) |
| `ACK` / `NACK` | Manual or auto (see client guide) | Subscriber ACK callbacks on outbound |
| `RECEIPT` | Producer send confirmation | — |
| `DISCONNECT` | On `close()` | Session teardown |
| `BEGIN` / `COMMIT` / `ABORT` | — | Not supported |

## Kafka bridge (`stomp4j-kafka-bridge`)

| Feature | Status | Details |
|---------|--------|---------|
| Embeddable bridge API | Supported | `StompKafkaBridge.builder()` — [kafka-bridge-guide.md](kafka-bridge-guide.md) |
| Runnable fat JAR | Supported | `stomp4j-kafka-bridge-runner` |
| Docker image / Compose | Supported | `samples/kafka-bridge-sample`, `samples/docker-compose.yaml` |
| STOMP `SEND` → Kafka produce | Supported | Prefix and explicit destination mapping |
| Kafka consume → STOMP `MESSAGE` | Supported | Refcounted consumer per destination |
| Multi-subscriber fan-out | Supported | Broadcast via `OutboundChannel` |
| STOMP TLS / auth | Supported | Reuses `StompServer` builder |
| Kafka SASL / SSL | Supported | `bridge.kafka.property.*` / env pass-through |
| Optional DLQ on produce failure | Supported | `bridge.dlq.topic` |
| Client-ack ↔ Kafka offset | Not supported | `auto` ack only |
| `/queue/` competing consumers | Not supported | Topic broadcast semantics only |
| STOMP transactions | Not supported | — |
| Multi-bridge replica coordination | Not supported | — |
| Schema Registry / Avro | Not supported | — |
| Spring Boot / Quarkus auto-config | Not supported | Use library or runner |

Normative behaviour: [STOMP specification](https://stomp.github.io/). Library terms: [domain-specification.md](domain-specification.md).

## Where to go next

| Goal | Document |
|------|----------|
| First working client or server | [getting-started.md](getting-started.md) |
| Client API in depth | [client-guide.md](client-guide.md) |
| Spring Boot integration | [spring-guide.md](spring-guide.md) |
| Quarkus integration | [quarkus-guide.md](quarkus-guide.md) |
| Embedded server patterns | [server-guide.md](server-guide.md) |
| STOMP ↔ Kafka bridge | [kafka-bridge-guide.md](kafka-bridge-guide.md) |
| SPI, TLS, wire format | [advanced-topics.md](advanced-topics.md) |
| Design philosophy | [overview.md](overview.md) |
