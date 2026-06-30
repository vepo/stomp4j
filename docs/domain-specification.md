# Domain Specification — Stomp4J

STOMP protocol vocabulary and library-specific terms. Agents must align code and tests with this language.

## Ubiquitous Language

### Protocol (STOMP spec)

| Term | Meaning |
|------|---------|
| **Frame** | A STOMP message on the wire: command, headers, body, terminated by NUL |
| **Command** | First line of a frame (`CONNECT`, `STOMP`, `CONNECTED`, `SEND`, `SUBSCRIBE`, `MESSAGE`, `ACK`, `NACK`, `UNSUBSCRIBE`, `DISCONNECT`, `BEGIN`, `COMMIT`, `ABORT`, `ERROR`, `RECEIPT`, …) |
| **Header** | Key-value metadata on a frame (`destination`, `content-type`, `subscription`, `ack`, `heart-beat`, …) |
| **Destination** | Logical address for send/subscribe (topic or queue path, e.g. `/topic/foo`) |
| **Subscription** | Client interest in a destination; identified by `id` header (1.1+) |
| **Acknowledgement (ACK/NACK)** | Client confirms or rejects a `MESSAGE` when `ack` mode requires it |
| **Heart-beat** | Negotiated idle keep-alive between client and broker |
| **Session** | A single connection's protocol state from CONNECT through DISCONNECT |

### Library modules

| Term | Meaning |
|------|---------|
| **Transport** | Byte stream under STOMP (TCP or WebSocket) |
| **TransportProvider** | SPI that maps a URL scheme to a `Transport` implementation |
| **Channel** | Server-side transport endpoint listening on a port |
| **OutboundChannel** | Server API to push frames to subscribed sessions (broadcast or per-session) |
| **StompMessage** | Inbound client `SEND` delivered to `MessageHandler` with destination, body, headers, and session channel |
| **StompSession** | Read-only view of a connected client (`login`, negotiated `version`, `outboundChannel`) |
| **MessageHandler** | Callback invoked when a client `SEND`s to a destination (`onSend(StompMessage)`) |
| **SubscriptionHandler** | Predicate deciding whether a session may `SUBSCRIBE` to a destination; optional `onSubscribed` / `onUnsubscribed` lifecycle callbacks |
| **StompAuthenticator** | Callback validating credentials on `CONNECT` |
| **Protocol version** | `1.0`, `1.1`, or `1.2` — negotiated from `accept-version` on `CONNECT` |

### Transports (TLS)

| Term | Meaning |
|------|---------|
| **stomps://** | STOMP over TLS TCP (`SecureTcpTransportProvider`) |
| **wss://** | STOMP over TLS WebSocket (`SecureWebSocketTransportProvider`) |

### Client API

| Term | Meaning |
|------|---------|
| **StompClient** | Public facade for connect, subscribe, send, close |
| **UserCredential** | Login/passcode (or env-derived) for `CONNECT` |
| **Callback subscription** | `subscribe(destination, Consumer<String>)` — push delivery |
| **Polling subscription** | `subscribe(destination)` then `hasData()` / `poll()` |
| **AckMode** | `AUTO`, `CLIENT`, `CLIENT_INDIVIDUAL` — `ack` header on `SUBSCRIBE` |
| **StompDelivery** | Inbound `MESSAGE` with `ack()` / `nack()` for manual acknowledgement |
| **StompReceipt** | Correlates producer `SEND` with broker `RECEIPT` frame |
| **StompTransaction** | Client-side `BEGIN` / `COMMIT` / `ABORT` scope for deferred sends |
| **SubscribeOptions** | `ack` mode and custom headers for `SUBSCRIBE` |
| **SendOptions** | `content-type`, optional receipt, and custom headers for `SEND` |
| **SubscriberAckListener** | Server callback when a client ACKs/NACKs an outbound `MESSAGE` |

### Spring Boot (optional)

| Term | Meaning |
|------|---------|
| **StompListener** | Declarative consumer method (`destination`, `ackMode`) |
| **Acknowledgment** | Spring-facing manual ack/nack for `@StompListener` methods |
| **StompClientTemplate** | High-level client send API with lifecycle managed by Spring |
| **StompOutboundTemplate** | Embedded server outbound push with optional subscriber ACK callbacks |

### Kafka bridge (optional)

| Term | Meaning |
|------|---------|
| **Bridge** | `StompKafkaBridge` process routing STOMP traffic to Kafka and back |
| **Destination mapping** | Rule translating a STOMP `destination` to a Kafka topic name |
| **Topic consumer** | Kafka consumer started when the first client subscribes to a destination; stopped when the last unsubscribes |
| **Prefix mapping** | Strip a STOMP prefix (for example `/topic/`) and map `/` to `.` in the Kafka topic |

## Invariants

- Every frame on the wire ends with a NUL byte.
- Client must receive `CONNECTED` before subscribing or sending.
- Server `SubscriptionHandler` runs before accepting a `SUBSCRIBE`; denied subscribe yields `ERROR` and closes the session.
- STOMP 1.2 `ACK`/`NACK` use the `id` header matching the `ack` header on `MESSAGE` (fallback to `message-id` for broker quirks).
- Transactional `SEND`/`ACK`/`NACK` are deferred until `COMMIT`; `ABORT` or `DISCONNECT` discards them.
- Internal packages (`client.internal`, `server.channels`, `server.session`) are not part of the public API.
- SPI implementations must be registered in both `module-info.java` and `META-INF/services`.

## When extending the domain

Before adding new commands, transports, or server features:

1. Add terms to the tables above.
2. Update [ARCHITECTURE.md](../ARCHITECTURE.md) §3 and §13 if boundaries or WIP status change.
3. Use domain terms in class, method, and test names — not generic technical names.
