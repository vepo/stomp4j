# Domain Specification — Stomp4J

STOMP protocol vocabulary and library-specific terms. Agents must align code and tests with this language.

## Ubiquitous Language

### Protocol (STOMP spec)

| Term | Meaning |
|------|---------|
| **Frame** | A STOMP message on the wire: command, headers, body, terminated by NUL |
| **Command** | First line of a frame (`CONNECT`, `CONNECTED`, `SEND`, `SUBSCRIBE`, `MESSAGE`, `ACK`, `NACK`, `UNSUBSCRIBE`, `DISCONNECT`, `ERROR`, …) |
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
| **SubscriptionHandler** | Predicate deciding whether a session may `SUBSCRIBE` to a destination |
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

## Invariants

- Every frame on the wire ends with a NUL byte.
- Client must receive `CONNECTED` before subscribing or sending.
- Server `SubscriptionHandler` runs before accepting a `SUBSCRIBE`.
- Internal packages (`client.internal`, `server.channels`, `server.session`) are not part of the public API.
- SPI implementations must be registered in both `module-info.java` and `META-INF/services`.

## When extending the domain

Before adding new commands, transports, or server features:

1. Add terms to the tables above.
2. Update [ARCHITECTURE.md](../ARCHITECTURE.md) §3 and §12 if boundaries or WIP status change.
3. Use domain terms in class, method, and test names — not generic technical names.
