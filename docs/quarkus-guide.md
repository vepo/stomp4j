# Quarkus guide

Use Stomp4J from Quarkus 3.17+ via optional extensions and **CDI Events** — no `*Template` APIs. Core modules stay framework-free; Quarkus support lives under `stomp4j-quarkus-client` and `stomp4j-quarkus-server`.

For runnable demos see [samples/README.md](../samples/README.md).

## Dependencies

**STOMP client** (connect to ActiveMQ Artemis, RabbitMQ STOMP plugin, etc.):

```xml
<dependency>
    <groupId>dev.vepo</groupId>
    <artifactId>stomp4j-quarkus-client</artifactId>
    <version>1.1.1</version>
</dependency>
```

**Embedded STOMP server**:

```xml
<dependency>
    <groupId>dev.vepo</groupId>
    <artifactId>stomp4j-quarkus-server</artifactId>
    <version>1.1.1</version>
</dependency>
```

Both extensions require `quarkus-arc` (pulled transitively).

## Client configuration

```properties
stomp4j.client.enabled=true
stomp4j.client.url=stomp://localhost:61613
stomp4j.client.username=user
stomp4j.client.password=passwd
stomp4j.client.transport-type=TCP
stomp4j.client.default-ack-mode=CLIENT_INDIVIDUAL
stomp4j.client.default-dispatch=ASYNC
stomp4j.client.receipt-timeout=30S
```

| Property | Default | Purpose |
|----------|---------|---------|
| `stomp4j.client.enabled` | `true` | Toggle client lifecycle |
| `stomp4j.client.url` | `stomp://localhost:61613` | Broker URL |
| `stomp4j.client.username` / `password` | — | `CONNECT` credentials |
| `stomp4j.client.default-ack-mode` | `CLIENT_INDIVIDUAL` | Default subscribe ACK when observer has no `Acknowledgment` parameter |
| `stomp4j.client.default-dispatch` | `ASYNC` | Fallback when inbound observer has no `@StompSync` / `@StompAsync` |
| `stomp4j.client.receipt-timeout` | `30S` | `SEND` receipt timeout |

## Outbound messages (CDI Events)

Inject a qualified `Event<StompOutboundMessage>` and fire synchronously or asynchronously:

```java
@ApplicationScoped
public class OrderPublisher {

    @Inject
    @StompSync
    Event<StompOutboundMessage> stompSync;

    public void publish(String json) {
        stompSync.fire(StompOutboundMessage.of("/queue/orders", json)
                                            .withReceipt(Duration.ofSeconds(30)));
    }
}
```

| Qualifier | Fire with | Behaviour |
|-----------|-----------|-----------|
| `@StompSync` | `event.fire(...)` | Blocks until send (and receipt, if requested) completes |
| `@StompAsync` | `event.fireAsync(...)` | Non-blocking; observe `StompOutboundCompleted` for result |

Async completion:

```java
void onCompleted(@Observes StompOutboundCompleted event) {
    if (event.failure() != null) {
        // handle error
    }
}
```

## Inbound messages (`@Observes`)

Subscribe by destination with `@StompDestination` and optional dispatch qualifier:

```java
@ApplicationScoped
public class OrderConsumer {

    void onOrder(@Observes @StompAsync @StompDestination("/queue/orders") StompInboundMessage message) {
        var body = message.delivery().body();
        Acknowledgment.of(message.delivery()).acknowledge();
    }
}
```

| Annotation | Role |
|------------|------|
| `@StompDestination("/path")` | Required destination qualifier |
| `@StompSync` / `@StompAsync` | Dispatch mode (default: `stomp4j.client.default-dispatch`) |
| `Acknowledgment` parameter | Switches subscribe to manual ACK (see [spring-guide.md](spring-guide.md#acknowledgements)) |

The extension registers subscriptions at build time (and falls back to runtime scan). Inbound observers run off the STOMP I/O thread when `@StompAsync` is used.

## Server configuration

```properties
stomp4j.server.enabled=true
stomp4j.server.server-name=stomp4j
stomp4j.server.heartbeat=30S
stomp4j.server.channels[0].type=TCP
stomp4j.server.channels[0].port=5520
stomp4j.server.channels[1].type=WEB_SOCKET
stomp4j.server.channels[1].port=5521
```

The embedded server starts on application startup.

## Server handlers

Implement `StompInboundHandler` (and optionally `StompAuthenticator`, `SubscriptionHandler`):

```java
@ApplicationScoped
public class ChatHandler implements StompInboundHandler, StompAuthenticator {

    @Inject
    StompServer server;

    @Override
    public boolean supports(String destination) {
        return destination.startsWith("/app/chat/");
    }

    @Override
    public void onSend(StompMessage message) {
        var room = message.destination().substring("/app/chat/".length());
        server.outboundChannel().send(new Message(Command.SEND,
            Headers.builder().with(Header.DESTINATION, "/topic/chat/" + room).build(),
            message.body()));
    }

    @Override
    public boolean authenticate(Credentials credentials) {
        return "demo".equals(credentials.username());
    }
}
```

Use `server.outboundChannel()` to fan out to subscribed clients. `sessionChannel()` writes raw frames to a single session only.

## Server outbound events

```java
@Inject
@StompSync
Event<StompServerMessage> stompServerSync;

void broadcast() {
    stompServerSync.fire(StompServerMessage.to("/topic/alerts", "payload"));
}
```

## Native image

Quarkus native image is **not supported in v1**. Use JVM mode.

## Related

| Topic | Document |
|-------|----------|
| ACK semantics (shared with Spring) | [spring-guide.md](spring-guide.md) |
| Core client API | [client-guide.md](client-guide.md) |
| Core server API | [server-guide.md](server-guide.md) |
| Capability checklist | [features.md](features.md) |
