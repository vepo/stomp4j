# Server guide

How to embed `StompServer` in your application. For a minimal example, see [getting-started.md#embedded-server](getting-started.md#embedded-server).

## Builder API

```java
try (var server = StompServer.builder()
        .channel(TransportType.TCP, 5500)
        .channel(TransportType.WEB_SOCKET, 5501)
        .subscription(topic -> topic.startsWith("/app/"))
        .authenticator(credentials -> "user".equals(credentials.username()))
        .handler(message -> { /* inbound SEND */ })
        .connectionListener(new StompConnectionListener() {
            @Override
            public void onConnected(StompSession session) { }
            @Override
            public void onDisconnected(StompSession session) { }
        })
        .supportedVersions("1.2", "1.1")
        .heartbeat(Duration.ofSeconds(10))
        .serverName("my-stomp-service")
        .ssl(sslContext)   // optional TLS for stomps/wss
        .start()) {

    // server running …
}
```

| Builder method | Required | Purpose |
|----------------|----------|---------|
| `channel(type, port)` | At least one | Listen on TCP and/or WebSocket |
| `handler(MessageHandler)` | Yes | Process inbound `SEND` frames |
| `subscription(SubscriptionHandler)` | Yes | Approve or deny `SUBSCRIBE` |
| `authenticator(StompAuthenticator)` | No | Validate `CONNECT` credentials |
| `connectionListener` | No | Session connect/disconnect hooks |
| `supportedVersions` | No | Default: 1.2, 1.1, 1.0 |
| `heartbeat` | No | Negotiated heart-beat interval |
| `serverName` | No | `server` header on `CONNECTED` |
| `ssl(SSLContext, …)` | No | Enable TLS on channels |

`start()` on the builder constructs, starts, and returns the `StompServer` instance.

## MessageHandler — inbound SEND

When a client sends a `SEND` frame, the server invokes your handler with a `StompMessage`:

```java
.handler(message -> {
    String destination = message.destination();
    String body = message.body();
    Headers headers = message.headers();
    OutboundChannel reply = message.sessionChannel();

    // business logic …
})
```

`StompMessage` is a record: `(destination, body, headers, sessionChannel)`.

## SubscriptionHandler — authorise SUBSCRIBE

Return `true` to accept, `false` to reject the subscription:

```java
.subscription(topic -> topic.startsWith("/public/"))
```

Runs before the server acknowledges the subscription.

## StompAuthenticator — CONNECT

```java
.authenticator(credentials ->
    "admin".equals(credentials.username())
        && "secret".equals(credentials.password()))
```

When configured, invalid credentials cause the connection to be rejected with an `ERROR` frame.

## Outbound messages

### Broadcast to all subscribers

`StompServer.outboundChannel()` fans out to every active session on all channels:

```java
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Headers;
import dev.vepo.stomp4j.commons.protocol.Message;

server.outboundChannel().send(
    new Message(
        Command.SEND,
        Headers.builder().with(Header.DESTINATION, "topic-1").build(),
        "MESSAGE-1"));
```

Subscribed clients receive `MESSAGE` frames on that destination.

### Reply to a specific session

Use `message.sessionChannel()` inside the handler to respond only to the sender:

```java
.handler(message -> {
    message.sessionChannel().send(
        new Message(
            Command.MESSAGE,
            Headers.builder()
                   .with(Header.DESTINATION, "topic-reply")
                   .with(Header.SUBSCRIPTION, "1")
                   .with(Header.MESSAGE_ID, "reply-1")
                   .build(),
            "REPLY-1"));
})
```

Build outbound frames with `Message` and `Headers` from `stomp4j-commons` — see [advanced-topics.md#wire-format](advanced-topics.md#wire-format).

## Transports

| `TransportType` | URL clients use |
|-----------------|-----------------|
| `TCP` | `stomp://localhost:<port>` or `stomps://…` with TLS |
| `WEB_SOCKET` | `ws://localhost:<port>` or `wss://…` with TLS |

You can expose both on different ports in the same server instance.

## Lifecycle

- `StompServer.builder()…start()` — opens channels and accepts connections.
- `close()` — stops channels and releases resources (use try-with-resources).
- Each TCP/WebSocket connection is represented by a `StompSession` (via `StompConnectionListener` or handler reply channels).

## Testing pattern

Integration tests in this repository start an embedded server and drive it with `stomp4j-client`:

```java
try (var server = StompServer.builder()…start();
     var client = StompClient.create("stomp://localhost:5500")) {
    client.connect();
    // exercise subscribe / send / receive
}
```

## Next steps

- TLS configuration: [advanced-topics.md#tls](advanced-topics.md#tls)
- Protocol details and SPI: [advanced-topics.md](advanced-topics.md)
