# Stomp4J samples

Runnable demos for Stomp4J — with and without Spring Boot.

## Prerequisites

- Docker and Docker Compose
- Maven 3.9+ and Java 21 (for local runs without Docker)

## Run with Docker Compose

From the repository root:

```bash
docker compose -f samples/docker-compose.yaml up --build
```

| Service | URL | Purpose |
|---------|-----|---------|
| Artemis | `stomp://localhost:61613` | Broker for client samples |
| Spring client | http://localhost:8080 | REST publish + `@StompListener` consumer |
| Plain client | http://localhost:8082 | REST publish + manual ACK consumer (no Spring) |
| Spring server | STOMP TCP `localhost:5500`, WebSocket `localhost:5501` | Embedded server via Spring starter |
| Plain server | STOMP TCP `localhost:5510`, WebSocket `localhost:5511` | Embedded server (plain Java API) |
| Quarkus client | http://localhost:8083 | REST publish + CDI inbound consumer |
| Quarkus server | http://localhost:8084, STOMP TCP `localhost:5520`, WebSocket `localhost:5521` | Embedded server via Quarkus extension |

### Client sample smoke test

Both client samples expose `POST /publish` and consume `/queue/demo.in` with manual ACK while publishing to `/queue/demo.out` with broker receipt.

```bash
curl -X POST http://localhost:8080/publish -H 'Content-Type: text/plain' -d 'hello-spring'
curl -X POST http://localhost:8082/publish -H 'Content-Type: text/plain' -d 'hello-plain'
curl -X POST http://localhost:8083/publish -H 'Content-Type: text/plain' -d 'hello-quarkus'
```

### Server sample

Both server samples implement the same chat echo:

- Connect with login `demo` (any passcode)
- Subscribe to `/topic/chat/lobby`
- Send to `/app/chat/lobby` — the server echoes to `/topic/chat/lobby`

| Sample | STOMP TCP | WebSocket |
|--------|-----------|-----------|
| Spring | `stomp://localhost:5500` | `ws://localhost:5501/` |
| Plain | `stomp://localhost:5510` | `ws://localhost:5511/` |
| Quarkus | `stomp://localhost:5520` | `ws://localhost:5521/` |

## Local Maven run

Start Artemis (optional):

```bash
docker compose -f scripts/docker/docker-compose.yaml up
```

### Spring Boot samples

```bash
mvn -pl samples/spring-client-sample spring-boot:run
mvn -pl samples/spring-server-sample spring-boot:run
```

See [docs/spring-guide.md](../docs/spring-guide.md) for configuration properties and API details.

### Quarkus samples

```bash
mvn -pl samples/quarkus-client-sample quarkus:dev
mvn -pl samples/quarkus-server-sample quarkus:dev
```

See [docs/quarkus-guide.md](../docs/quarkus-guide.md) for configuration properties and CDI Event patterns.

### Plain Java samples

Package and run the shaded JARs:

```bash
mvn -pl samples/plain-client-sample -am package -DskipTests
java -jar samples/plain-client-sample/target/plain-client-sample-*.jar

mvn -pl samples/plain-server-sample -am package -DskipTests
java -jar samples/plain-server-sample/target/plain-server-sample-*.jar
```

Environment variables (plain client):

| Variable | Default | Description |
|----------|---------|-------------|
| `STOMP4J_CLIENT_URL` | `stomp://localhost:61613` | Broker URL |
| `STOMP4J_CLIENT_USERNAME` | `user` | CONNECT login |
| `STOMP4J_CLIENT_PASSWORD` | `passwd` | CONNECT passcode |
| `HTTP_PORT` | `8082` | Publish endpoint port |

Environment variables (plain server):

| Variable | Default | Description |
|----------|---------|-------------|
| `STOMP_TCP_PORT` | `5510` | STOMP TCP listen port |
| `STOMP_WS_PORT` | `5511` | WebSocket listen port |

For library usage without Spring, see [README.md](../README.md) and [docs/getting-started.md](../docs/getting-started.md).
