# Kafka bridge guide

Bidirectional routing between STOMP clients and Apache Kafka topics using `stomp4j-kafka-bridge`.

## Artifacts

| Artifact | Use |
|----------|-----|
| `stomp4j-kafka-bridge` | Embeddable library API |
| `stomp4j-kafka-bridge-runner` | Executable fat JAR (`java -jar`) |
| `samples/kafka-bridge-sample` | Docker image build |

Maven coordinates (build from source while on `1.1.1-SNAPSHOT`):

```xml
<dependency>
    <groupId>dev.vepo</groupId>
    <artifactId>stomp4j-kafka-bridge</artifactId>
    <version>1.1.1-SNAPSHOT</version>
</dependency>
```

## Library API

```java
try (var bridge = StompKafkaBridge.builder()
        .kafkaConfig(KafkaBridgeConfig.builder()
                .bootstrapServers("localhost:9092")
                .build())
        .destinationMapping(DestinationMapping.prefix("/topic/"))
        .allowedDestinationPrefix("/topic/")
        .channel(TransportType.TCP, 61613)
        .channel(TransportType.WEB_SOCKET, 61614)
        .start()) {

  bridge.awaitShutdown();
}
```

### Destination mapping

Default prefix mapping:

| STOMP destination | Kafka topic |
|-------------------|-------------|
| `/topic/orders` | `orders` |
| `/topic/chat/lobby` | `chat.lobby` |

Explicit overrides:

```java
DestinationMapping.composite(
    DestinationMapping.explicit(Map.of("/topic/legacy", "legacy-topic")),
    DestinationMapping.prefix("/topic/"));
```

### STOMP → Kafka

Inbound `SEND` frames are published to the mapped Kafka topic. Optional record key via STOMP header `kafka-key`.

### Kafka → STOMP

When a client `SUBSCRIBE`s, the bridge starts a Kafka consumer for the mapped topic (refcounted per destination) and delivers records as STOMP `MESSAGE` frames to all subscribers on that destination.

## Runnable JAR

```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
java -jar stomp4j-kafka-bridge-runner.jar
```

Configuration precedence: environment variables, then `application.properties` on the classpath.

| Variable / property | Default | Purpose |
|---------------------|---------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` / `bridge.kafka.bootstrap-servers` | — | Required |
| `STOMP_TCP_PORT` / `bridge.stomp.tcp-port` | `61613` | STOMP TCP port |
| `STOMP_WS_PORT` / `bridge.stomp.ws-port` | `61614` | STOMP WebSocket port |
| `DESTINATION_PREFIX` / `bridge.destination.prefix` | `/topic/` | Allowed subscription prefix |
| `TOPIC_PREFIX_STRIP` / `bridge.topic.prefix-strip` | `/topic/` | Mapping input prefix |
| `KAFKA_CONSUMER_GROUP_ID` / `bridge.kafka.consumer-group-id` | `stomp4j-bridge` | Base consumer group |
| `STOMP_AUTH_USERNAME` / `bridge.stomp.auth.username` | — | Optional CONNECT login |
| `STOMP_AUTH_PASSWORD` / `bridge.stomp.auth.password` | — | Optional CONNECT passcode |
| `BRIDGE_DLQ_TOPIC` / `bridge.dlq.topic` | — | Optional DLQ for failed produces |
| `bridge.mapping.<stomp-destination>` | — | Explicit STOMP → Kafka map |
| `bridge.kafka.property.<key>` | — | Pass-through to `kafka-clients` |

Kafka SASL/SSL: set standard `bridge.kafka.property.*` entries (for example `bridge.kafka.property.security.protocol=SASL_SSL`).

## Docker

From the repository root:

```bash
docker compose -f samples/docker-compose.yaml up kafka stomp-kafka-bridge
```

Connect STOMP clients to `stomp://localhost:61613` or `ws://localhost:61614/`.

## Limitations

- STOMP `auto` ack only (offset committed after outbound delivery)
- Broadcast topic semantics only (not `/queue/` competing consumers)
- No STOMP transactions (`BEGIN` / `COMMIT` / `ABORT`)
- Single bridge instance per consumer group (no multi-replica coordination)

See [features.md#kafka-bridge-stomp4j-kafka-bridge](features.md#kafka-bridge-stomp4j-kafka-bridge) for the capability checklist.
