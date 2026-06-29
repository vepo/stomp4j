# Spring Boot guide

Use Stomp4J from Spring Boot via optional starters. Core modules remain framework-free; Spring support lives in `stomp4j-spring-boot-starter-client` and `stomp4j-spring-boot-starter-server`.

For runnable demos see [samples/README.md](../samples/README.md).

## Dependencies

**STOMP client** (connect to ActiveMQ Artemis, RabbitMQ STOMP plugin, etc.):

```xml
<dependency>
    <groupId>dev.vepo</groupId>
    <artifactId>stomp4j-spring-boot-starter-client</artifactId>
    <version>1.1.1</version>
</dependency>
```

**Embedded STOMP server**:

```xml
<dependency>
    <groupId>dev.vepo</groupId>
    <artifactId>stomp4j-spring-boot-starter-server</artifactId>
    <version>1.1.1</version>
</dependency>
```

## Client configuration

```yaml
stomp4j:
  client:
    enabled: true
    url: stomp://localhost:61613
    username: user
    password: passwd
    transport-type: TCP          # optional: TCP | WEB_SOCKET
    default-ack-mode: CLIENT_INDIVIDUAL
    receipt-timeout: 30s
```

| Property | Default | Purpose |
|----------|---------|---------|
| `stomp4j.client.enabled` | `true` | Toggle auto-configuration |
| `stomp4j.client.url` | `stomp://localhost:61613` | Broker URL |
| `stomp4j.client.username` / `password` | — | `CONNECT` credentials |
| `stomp4j.client.default-ack-mode` | `CLIENT_INDIVIDUAL` | Default for `@StompListener` when `ackMode = AUTO` |
| `stomp4j.client.receipt-timeout` | `30s` | `SEND` receipt future timeout |

### `StompClientTemplate`

Inject and send messages without managing the connection lifecycle:

```java
@Service
public class OrderPublisher {
    private final StompClientTemplate stomp;

    public OrderPublisher(StompClientTemplate stomp) {
        this.stomp = stomp;
    }

    public void publish(String json) throws Exception {
        var receipt = stomp.sendWithReceipt("/queue/orders", json);
        receipt.completion().get(); // broker accepted the SEND
    }
}
```

### `@StompListener`

Declarative consumers with optional manual ACK/NACK:

```java
@Component
public class OrderConsumer {

    @StompListener(destination = "/queue/orders", ackMode = AckMode.CLIENT_INDIVIDUAL)
    public void onOrder(String body, Acknowledgment ack) {
        process(body);
        ack.acknowledge();
    }
}
```

| `ackMode` | Behaviour |
|-----------|-----------|
| `AUTO` | Ack after successful method return |
| `CLIENT` / `CLIENT_INDIVIDUAL` | Must call `ack.acknowledge()` or `ack.nack()` (or use `StompDelivery`) |
| Exception | `nack()` when manual mode and not yet acked |

Inject headers with `@StompHeader("message-id")` on `String` parameters.

## Server configuration

```yaml
stomp4j:
  server:
    enabled: true
    server-name: my-app
    heartbeat: 30s
    channels:
      - type: TCP
        port: 5500
      - type: WEB_SOCKET
        port: 5501
```

### Inbound handlers

Implement `StompInboundHandler` beans — routed by `supports(destination)`:

```java
@Component
public class ChatHandler implements StompInboundHandler {
    @Override
    public boolean supports(String destination) {
        return destination.startsWith("/app/chat/");
    }

    @Override
    public void onSend(StompMessage message) {
        // reply via message.sessionChannel()
    }
}
```

Optional beans: `StompAuthenticator`, `SubscriptionHandler`, `StompConnectionListener`.

### Outbound push with subscriber ACK callbacks

```java
@Service
public class Alerts {
    private final StompOutboundTemplate outbound;

    public void broadcast(String text) {
        var message = new Message(Command.SEND,
            Headers.builder().with(Header.DESTINATION, "/topic/alerts").build(),
            text);
        outbound.send(message, StompOutboundTemplate.SubscriberAckCallbacks.builder()
            .onSubscriberAck((id, session) -> log.info("Ack from {}", id))
            .onSubscriberNack((id, session) -> log.warn("Nack from {}", id))
            .build());
    }
}
```

## ACK semantics

| Term | Meaning |
|------|---------|
| **Producer receipt** | Broker accepted your `SEND` (`receipt` header → `RECEIPT` frame). Client → external broker only. |
| **Consumer ACK/NACK** | Your app processed an inbound `MESSAGE`. Use `@StompListener` + `Acknowledgment` or core `StompDelivery`. |
| **Subscriber ACK** | Embedded server learns a connected client acked/nacked a `MESSAGE` you pushed via `StompOutboundTemplate`. |

External brokers do **not** push consumer-ack events back to publishers on topics — use subscriber ACK callbacks only with the embedded server, or application-level reply destinations.

## Related docs

- [client-guide.md](client-guide.md) — core client API
- [server-guide.md](server-guide.md) — core server API
- [features.md](features.md) — capability checklist
