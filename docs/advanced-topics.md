# Advanced topics

Topics for integrators extending Stomp4J or working close to the wire. Read [overview.md](overview.md) and the client/server guides first.

## Protocol versions

Stomp4J implements [STOMP 1.0](https://stomp.github.io/stomp-1.0.html), [1.1](https://stomp.github.io/stomp-1.1.html), and [1.2](https://stomp.github.io/stomp-1.2.html).

### Client

Version classes live in `dev.vepo.stomp4j.client.protocol.v1_*`:

```java
import dev.vepo.stomp4j.client.protocol.v1_0.Stomp1_0;
import dev.vepo.stomp4j.client.protocol.v1_1.Stomp1_1;
import dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;

StompClient.create(url, credentials, Set.of(new Stomp1_1(), new Stomp1_2()));
```

The broker picks the highest mutually supported version in `CONNECTED`.

Differences that matter in practice:

| Feature | 1.0 | 1.1+ |
|---------|-----|------|
| Subscription `id` header | No | Yes |
| Heart-beat negotiation | No | Yes |
| `NACK` | No | Yes (1.1+) |

### Server

Configure offered versions on the builder:

```java
StompServer.builder()
    .supportedVersions("1.2", "1.1", "1.0")
```

Session negotiation and frame handling follow the selected version.

## Wire format

`stomp4j-commons` models STOMP frames:

```java
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Headers;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;

// Build
var frame = MessageBuilder.builder(Command.SEND)
    .header(Header.DESTINATION, "/topic/foo")
    .header(Header.CONTENT_TYPE, "text/plain")
    .body("hello")
    .build();

// Encode to bytes (ends with NUL)
byte[] encoded = frame.encode();

// Parse from stream buffer
var buffer = new MessageBuffer();
buffer.write(encoded);
Optional<Message> parsed = buffer.readMessage();
```

Use this API when constructing server outbound frames or building tooling on top of STOMP.

Domain terms: [domain-specification.md](domain-specification.md).

## JPMS (Java modules)

Each artifact is a JPMS module:

| Artifact | Module name | Exports (high level) |
|----------|-------------|----------------------|
| `stomp4j-commons` | `stomp4j.commons` | `commons`, `commons.protocol` |
| `stomp4j-client` | `stomp4j.client` | `client`, `client.protocol.*`, `client.transport` |
| `stomp4j-server` | `stomp4j.server` | `server`, `server.auth` |

Internal packages (`client.internal`, `server.session`, `server.channels`) are **not** exported. Depend on public types only.

In `module-info.java` of your application:

```java
module com.example.app {
    requires stomp4j.client;
    // or requires stomp4j.server;
}
```

## Extending transports

Implement `TransportProvider` and register via SPI:

1. Class implements `dev.vepo.stomp4j.client.transport.TransportProvider`.
2. Add `provides TransportProvider with …` in `module-info.java`.
3. Add `META-INF/services/dev.vepo.stomp4j.client.transport.TransportProvider` listing your implementation.

Follow the pattern in `TcpTransportProvider` and `WebSocketTransportProvider` inside the client module.

## Extending protocol versions

Implement `dev.vepo.stomp4j.client.protocol.Stomp` and register the same way (`provides Stomp with …` + `META-INF/services`).

Subclass responsibilities include building version-correct `SUBSCRIBE`, `SEND`, `ACK`, and `UNSUBSCRIBE` frames.

## TLS

### Client

Pass a configured `javax.net.ssl.SSLContext` to `StompClient.create` when using `stomps://` or `wss://`:

```java
StompClient.create("stomps://broker:61613", credentials, sslContext);
```

### Server

```java
StompServer.builder()
    .ssl(sslContext)
    // or with keystore path for reload scenarios:
    .ssl(sslContext, "/path/to/keystore.p12", "changeit")
```

See `StompServerTlsTest` in the repository for a working example with test keystores.

## Heart-beats

- **Client:** heart-beat headers are sent on `CONNECT` when the selected `Stomp` version supports them (`Stomp1_1`, `Stomp1_2`).
- **Server:** configure with `.heartbeat(Duration.ofSeconds(n))` on the builder.

Heart-beats keep idle connections alive per the STOMP spec; brokers may require them on long-lived subscriptions.

## SPI summary

| Extension point | Interface | Registration |
|-----------------|-----------|--------------|
| Transport | `TransportProvider` | `module-info` `provides` + `META-INF/services` |
| Protocol version | `Stomp` | `module-info` `provides` + `META-INF/services` |

ServiceLoader resolves implementations at runtime. When shading or repackaging, merge `META-INF/services` files.

## Contributing internals

Internal architecture, test layout, and CI: [ARCHITECTURE.md](../ARCHITECTURE.md).

Build and verify:

```bash
mvn verify   # requires Docker for integration tests
```
