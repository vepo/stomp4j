# Overview

Stomp4J is an open-source Java library that implements the [STOMP protocol](https://stomp.github.io/). It is **not** a message broker and **not** tied to Spring, Quarkus, or Jakarta EE — you add a Maven dependency and use the public API from your own application.

## Purpose

STOMP (Simple Text Oriented Messaging Protocol) is a text-based wire format for messaging. Brokers such as ActiveMQ Artemis expose STOMP endpoints so clients in many languages can publish and subscribe without broker-specific SDKs.

Stomp4J gives Java developers:

1. **A STOMP client** — connect to any STOMP broker over TCP or WebSocket, negotiate protocol version, subscribe, send, and acknowledge messages.
2. **An embeddable STOMP server** — listen on a port inside your JVM, route `SEND` frames to application code, and push `MESSAGE` frames back to subscribers. Useful for integration tests, prototypes, and small gateways.
3. **A shared protocol kernel** (`stomp4j-commons`) — encode and decode STOMP frames so client and server stay aligned with the specification.

The library targets **correctness against the STOMP spec**, a **small surface area**, and **clear separation** between public API, protocol logic, and transport I/O.

## Modules

Stomp4J is a Maven multi-module project. Published artifacts on Maven Central use group `dev.vepo`.

```
stomp4j-parent
├── stomp4j-commons   (wire format — frames, headers, commands)
├── stomp4j-client    → commons   (StompClient, transports, protocol versions)
└── stomp4j-server    → commons   (StompServer, handlers; Vert.x for WebSocket)
```

### stomp4j-commons

Low-level STOMP wire model: `Message`, `Command`, `Header`, `Headers`, `MessageBuffer`.

- **Typical consumers:** extension authors, advanced integrators, or contributors.
- **Most applications:** depend on `stomp4j-client` or `stomp4j-server`; commons comes transitively.

### stomp4j-client

Public entry point: `StompClient.create(url, …)`.

- TCP (`stomp://`, `stomps://`) and WebSocket (`ws://`, `wss://`) transports.
- STOMP 1.0, 1.1, and 1.2 via version-specific `Stomp` implementations (selected after `CONNECTED`).
- Callback or polling subscriptions.

### stomp4j-server

Public entry point: `StompServer.builder()…start()`.

- TCP and WebSocket channels on configurable ports.
- Application hooks: `MessageHandler` (inbound `SEND`), `SubscriptionHandler` (authorise `SUBSCRIBE`), `StompAuthenticator` (validate `CONNECT`).
- `OutboundChannel` to push frames to connected sessions.

Production `stomp4j-server` does **not** depend on `stomp4j-client`; only tests use the client to drive the server.

## Design philosophy

These ideas shape the API and are the style we recommend when you build on top of Stomp4J.

### Thin facade, rich internals

`StompClient` and `StompServer` are facades. Connection state machines, frame parsing, and transport details live in internal packages (`client.internal`, `server.session`, `server.channels`). **Use the public API** — do not depend on internal packages; they are not exported in JPMS and may change.

### Tell, don't ask

Call intention methods instead of inspecting and mutating state across layers:

```java
// Preferred — client owns the connection lifecycle
client.connect();
client.subscribe("/topic/foo", this::handleMessage);

// Not applicable — there is no public getter chain into transport/session internals
```

On the server, handlers receive what they need (`StompMessage` with destination, body, headers, and a reply channel) rather than raw session objects.

### Handlers as the application boundary

Your business logic plugs in at clear boundaries:

| Role | Interface | When it runs |
|------|-----------|--------------|
| Inbound messages | `MessageHandler` | Client `SEND` to a destination |
| Subscription policy | `SubscriptionHandler` | Before `SUBSCRIBE` is accepted |
| Authentication | `StompAuthenticator` | On `CONNECT` |
| Outbound push | `OutboundChannel` | You publish `MESSAGE` frames to subscribers |

Keep handlers small; delegate to your domain services inside them.

### Protocol-first

Behaviour follows the [STOMP specification](https://stomp.github.io/). Version differences (headers, heart-beats, ack modes) are handled in version-specific client code and server session configuration — not ad-hoc broker quirks.

### Plain Java, minimal dependencies

- Java 21, JPMS modules, SLF4J for logging.
- No DI framework required; construct clients and servers directly or wrap them in your own beans.
- Server WebSocket support uses Vert.x; client WebSocket uses the JDK `java.net.http` client.

### Resource lifecycle

Both `StompClient` and `StompServer` implement `AutoCloseable`. Use try-with-resources so connections and listening ports are released.

## What to read next

| Goal | Next document |
|------|---------------|
| See what is supported today | [features.md](features.md) |
| Run your first client or server | [getting-started.md](getting-started.md) |
| Client features in depth | [client-guide.md](client-guide.md) |
| Server features in depth | [server-guide.md](server-guide.md) |
| SPI, JPMS, wire format | [advanced-topics.md](advanced-topics.md) |
