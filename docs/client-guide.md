# Client guide

How to use `StompClient` against a STOMP broker. Start with [getting-started.md](getting-started.md) if you have not connected yet.

## Creating a client

Use static factory methods on `StompClient` — there is no public constructor.

```java
// Anonymous broker (no login headers)
StompClient.create("stomp://localhost:61613");

// With credentials (CONNECT login/passcode headers)
StompClient.create(url, new UserCredential("user", "pass"));

// Force transport when URL scheme is ambiguous
StompClient.create(url, credentials, TransportType.TCP);

// Restrict protocol versions offered in CONNECT
StompClient.create(url, credentials, Set.of(new Stomp1_2()));

// TLS — provide SSLContext (e.g. trust store for stomps:// or wss://)
StompClient.create("stomps://broker:61613", credentials, sslContext);
```

`UserCredential` maps to STOMP `login` and `passcode` headers on `CONNECT`.

## URL schemes and transports

| Scheme | Transport | Notes |
|--------|-----------|-------|
| `stomp://host:port` | TCP | Typical broker port (e.g. 61613) |
| `stomps://host:port` | TCP + TLS | Pass `SSLContext` to `create` |
| `ws://host:port/path` | WebSocket | Path depends on broker (e.g. `/stomp`) |
| `wss://host:port/path` | WebSocket + TLS | Pass `SSLContext` to `create` |

The client selects a `TransportProvider` via Java SPI from the URL scheme. Custom transports: [advanced-topics.md#extending-transports](advanced-topics.md#extending-transports).

## Connection lifecycle

```java
try (var client = StompClient.create(url, credentials)) {
    client.connect();           // transport up, CONNECT → CONNECTED
    // subscribe, send …
    client.close();             // DISCONNECT, release (automatic in try-with-resources)
}
```

If {@code connect()} throws, the client closes itself — do not reuse that instance. When not using
try-with-resources, call {@code close()} in a {@code finally} block only if you need to guard a
path where {@code connect()} succeeded; after a failed {@code connect()}, cleanup is already done.

- **`connect()`** returns `this` for chaining; releases resources automatically on failure.
- **`join()`** blocks the current thread until the client is closed — useful for callback-driven apps that have no other event loop.
- Always close the client to send `DISCONNECT` and free sockets.
- **`close(Duration)`** sends `DISCONNECT` with a `receipt` header and waits up to the grace period for the matching `RECEIPT` before closing the transport.

## Threading

**Design intent:** a `StompClient` should be usable from multiple application threads after `connect()` (concurrent `send`, subscribe while receiving). Full guarantees are still being hardened — see [ARCHITECTURE.md §4](../ARCHITECTURE.md#4-threading-and-concurrency) and §13 (known gaps).

| Topic | Behaviour today |
|-------|-----------------|
| **Callback subscriptions** | Your `Consumer` runs on the **transport I/O thread** (TCP read loop or WebSocket callback). Keep callbacks short; offload heavy work to your own executor. Do not touch non-thread-safe state without synchronisation. |
| **Polling subscriptions** | `hasData()` / `poll()` from your thread; inbound messages are queued per subscription. |
| **`join()`** | Blocks until `close()` completes on another thread. |
| **Concurrent API calls** | Prefer serialising `subscribe` / `unsubscribe` from one thread until thread-safety work in §4.6 is complete. |

For tests, avoid asserting from the test thread while messages arrive on the client I/O thread without `Awaitility` or latches — see ARCHITECTURE §4.5.

## Subscriptions

Two styles are supported.

### Callback (push)

```java
client.subscribe("/topic/orders", body -> {
    processOrder(body);
});
```

The consumer runs when a `MESSAGE` frame arrives for that subscription.

### Polling (pull)

```java
var sub = client.subscribe("/topic/orders");
if (sub.hasData()) {
    List<String> batch = sub.poll();
}
```

Use polling when you prefer to drive consumption from your own loop or executor.

### Unsubscribe

```java
client.unsubscribe("/topic/orders");
// or
client.unsubscribe(subscription);
```

### Options and manual acknowledgement

Use `SubscribeOptions` when you need a non-default `ack` mode or custom `SUBSCRIBE` headers:

```java
import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.SubscribeOptions;

client.subscribe("/topic/orders",
    SubscribeOptions.builder()
        .ackMode(AckMode.CLIENT_INDIVIDUAL)
        .header("selector", "region = 'EU'")
        .build(),
    delivery -> delivery.ack());
```

Polling subscriptions accept the same options: `client.subscribe(topic, SubscribeOptions.defaults())`.

For manual ack without custom headers:

```java
client.subscribe("/queue/work", AckMode.CLIENT_INDIVIDUAL, delivery -> {
    process(delivery.body());
    delivery.ack();   // or delivery.nack()
});
```

## Sending messages

```java
client.sendPlain("/queue/jobs", "{\"id\":1}", "application/json");
```

`sendPlain` sets `content-type` and sends a `SEND` frame. The destination string must match what your broker expects (often `/topic/…` or `/queue/…`).

### Receipts and custom headers

```java
import dev.vepo.stomp4j.client.SendOptions;

var receipt = client.send("/queue/jobs",
    payload,
    SendOptions.builder()
        .contentType("application/json")
        .header("priority", "9")
        .receipt(true)
        .build());
receipt.completion().get();
```

## Transactions

```java
try (var tx = client.beginTransaction()) {
    tx.send("/topic/events", "a", SendOptions.plainText());
    tx.send("/topic/events", "b", SendOptions.plainText());
    tx.commit();   // or tx.abort()
}
```

Transactional sends are visible to the server only after `commit()`.

## Protocol versions

On `CONNECT`, the client advertises accepted versions. After `CONNECTED`, it uses the version the broker selected.

```java
import dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;

StompClient.create(url, credentials, Set.of(new Stomp1_2()));
```

Omit the version set to offer all versions registered in SPI (`Stomp.ALL_VERSIONS`). Integration tests in this repository exercise 1.0, 1.1, and 1.2 against ActiveMQ Artemis.

## Real-world example

Public STOMP-over-WebSocket feed (credentials from environment):

```java
try (var client = StompClient.create(
        "ws://publicdatafeeds.networkrail.co.uk:61618",
        new UserCredential(System.getenv("USERNAME"), System.getenv("PASSWORD")))) {

    client.connect();
    client.subscribe("/topic/TRAIN_MVT_ALL_TOC", data -> {
        // parse train movement payload
    });
    client.join();
}
```

## Errors

Failed connects, protocol errors, and transport failures throw `dev.vepo.stomp4j.client.exceptions.StompException` (unchecked). Handle or propagate at your application boundary.

## Logging

Stomp4J uses SLF4J. Add a binding at runtime, for example:

```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.21</version>
    <scope>runtime</scope>
</dependency>
```

## Next steps

- Embed your own broker for tests: [server-guide.md](server-guide.md)
- TLS details, heart-beats, wire format: [advanced-topics.md](advanced-topics.md)
