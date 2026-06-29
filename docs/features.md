# Features

Canonical checklist of what Stomp4J supports today. Use this page to compare capabilities before adopting the library or planning an integration.

When you add, change, or remove user-visible behaviour, **update this page** in the same change — see [documentation.mdc](../.cursor/rules/documentation.mdc).

## At a glance

| Area | Highlights |
|------|------------|
| **Client** | TCP & WebSocket, TLS, STOMP 1.0–1.2, subscribe/send, callback & polling |
| **Server** | Embeddable TCP & WebSocket, handlers, auth, outbound push, TLS |
| **Commons** | Frame encode/decode, headers, commands |
| **Platform** | Java 21, JPMS modules, SLF4J, no application framework |

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
| `SEND` | Supported | `sendPlain(destination, body, contentType)` |
| Client-side `ACK` on `MESSAGE` | Supported | Automatic for negotiated 1.1+ sessions |
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
| Per-session reply | Supported | `StompMessage.sessionChannel()` |
| Connection lifecycle hooks | Supported | `StompConnectionListener` |
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
| Spring / Quarkus / Jakarta EE | Not required | Plain Java library |
| Maven artifacts on Maven Central | Supported | `dev.vepo:stomp4j-client`, `stomp4j-server`, `stomp4j-commons` |

## STOMP commands (summary)

| Command | Client | Server |
|---------|--------|--------|
| `CONNECT` / `CONNECTED` | Sends / receives | Accepts / responds |
| `SUBSCRIBE` / `UNSUBSCRIBE` | Sends | Accepts (with policy) |
| `SEND` | Sends | Receives via handler |
| `MESSAGE` | Receives | Sends (outbound) |
| `ACK` / `NACK` | Auto-ACK on receive (1.1+) | — |
| `DISCONNECT` | On `close()` | Session teardown |
| `BEGIN` / `COMMIT` / `ABORT` | — | Not supported |

Normative behaviour: [STOMP specification](https://stomp.github.io/). Library terms: [domain-specification.md](domain-specification.md).

## Where to go next

| Goal | Document |
|------|----------|
| First working client or server | [getting-started.md](getting-started.md) |
| Client API in depth | [client-guide.md](client-guide.md) |
| Embedded server patterns | [server-guide.md](server-guide.md) |
| SPI, TLS, wire format | [advanced-topics.md](advanced-topics.md) |
| Design philosophy | [overview.md](overview.md) |
