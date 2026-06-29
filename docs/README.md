# Stomp4J documentation

User-facing guides for library consumers. Maintainer references live at the repository root ([ARCHITECTURE.md](../ARCHITECTURE.md), [AGENTS.md](../AGENTS.md)).

## Learning paths

### I only need a STOMP client

1. [overview.md](overview.md) — what the library is and how it is organised
2. [getting-started.md](getting-started.md) — Maven dependency and first connection
3. [client-guide.md](client-guide.md) — transports, subscriptions, TLS, protocol versions

### I want to embed a STOMP server

1. [overview.md](overview.md)
2. [getting-started.md](getting-started.md#embedded-server)
3. [server-guide.md](server-guide.md) — handlers, outbound delivery, authentication

### I use Spring Boot

1. [spring-guide.md](spring-guide.md) — starters, `@StompListener`, embedded server beans
2. [samples/README.md](../samples/README.md) — Docker Compose demos

### I use Quarkus

1. [quarkus-guide.md](quarkus-guide.md) — extensions, CDI Events, `@StompDestination`
2. [samples/README.md](../samples/README.md) — Quarkus client/server samples (ports 8083/8084)

### I need STOMP ↔ Kafka bridging

1. [kafka-bridge-guide.md](kafka-bridge-guide.md) — library API, JAR, Docker
2. [samples/docker-compose.yaml](../samples/docker-compose.yaml) — `kafka` + `stomp-kafka-bridge` services

### I am extending or integrating deeply

1. [advanced-topics.md](advanced-topics.md) — SPI, JPMS modules, wire format, heart-beats
2. [domain-specification.md](domain-specification.md) — STOMP terms and invariants
3. [ARCHITECTURE.md](../ARCHITECTURE.md) — internal layout (contributors)

## Document map

| Document | Audience | Topics |
|----------|----------|--------|
| [features.md](features.md) | Everyone | Supported capabilities checklist (client, server, protocol) |
| [overview.md](overview.md) | Everyone | Purpose, modules, design philosophy |
| [getting-started.md](getting-started.md) | Beginner | Dependencies, minimal client & server |
| [client-guide.md](client-guide.md) | Intermediate | URLs, subscriptions, send, close lifecycle |
| [spring-guide.md](spring-guide.md) | Intermediate | Spring Boot starters, `@StompListener`, templates |
| [quarkus-guide.md](quarkus-guide.md) | Intermediate | Quarkus extensions, CDI Events, inbound `@Observes` |
| [kafka-bridge-guide.md](kafka-bridge-guide.md) | Intermediate | STOMP ↔ Kafka bridge, mapping, deployment |
| [advanced-topics.md](advanced-topics.md) | Advanced | Versions, SPI, TLS, commons protocol API |
| [domain-specification.md](domain-specification.md) | Contributors | Ubiquitous language, protocol invariants |
