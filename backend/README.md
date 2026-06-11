# Eventore backend (multi-module)

## Layout

```
backend/
  pom.xml                    # Parent aggregator
  openapi/eventore-api.yaml  # OpenAPI 3.0 contract (canonical)
  eventore-core/             # Domain, SPI, StreamProvider contract
  eventore-provider-kafka/   # Kafka + admin
  eventore-provider-mqtt/
  eventore-provider-jms/
  eventore-provider-pulsar/
  eventore-provider-rabbitmq/
  eventore-provider-kinesis/     # AWS
  eventore-provider-gcp-pubsub/  # GCP
  eventore-provider-azure-servicebus/
  eventore-server/           # Spring Boot app, REST, WebSocket, Swagger UI
```

Each `eventore-provider-*` module:

- Implements `com.eventore.provider.StreamProvider`
- Registers via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Selected at **build time** via Maven profiles on `eventore-server/pom.xml`
- Filtered at **runtime** with `eventore.enabled-protocols` (comma-separated)

## Use what is required

Pick only the integration modules you need so the JAR, image, and UI stay small.

| Layer | Control |
|-------|---------|
| Build | Maven profile on `eventore-server` (e.g. `-Pkafka-kinesis -P!providers-all`) |
| Runtime | `eventore.enabled-protocols=KAFKA,KINESIS` or env / Helm `enabledProtocols` |
| UI | `GET /api/v1/config` → `supportedProtocols` drives connection options |

**Kafka + Kinesis example:**

```bash
cd backend
mvn -DskipTests -Pkafka-kinesis -P!providers-all package
java -jar eventore-server/target/eventore-server-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=kafka-kinesis
```

**Docker slim image:**

```bash
docker build --build-arg EVENTORE_MAVEN_PROFILES=kafka-kinesis -f docker/Dockerfile.backend .
```

**Helm:**

```bash
helm install eventore ./deploy/helm/eventore -f deploy/helm/eventore/values-kafka-kinesis.yaml
```

Profiles: `providers-all` (default), `kafka-kinesis`, `provider-kafka`, `provider-kinesis`, …

## Build & run

```bash
cd backend
mvn -DskipTests package
java -jar eventore-server/target/eventore-server-0.1.0-SNAPSHOT.jar
```

## OpenAPI (per-stream, code-generated)

Specs live under `openapi/streams/` (core, inspect, diagnostics, kafka, kinesis). The `eventore-api-codegen` module generates Spring controllers + delegate interfaces; `eventore-server` implements delegates in `com.eventore.api.delegate`.

| Stream | Spec | Delegate |
|--------|------|----------|
| core | `streams/core-api.yaml` | `Core*ApiDelegateImpl` |
| inspect | `streams/inspect-api.yaml` | `InspectApiDelegateImpl` |
| diagnostics | `streams/diagnostics-api.yaml` | `DiagnosticsController` (hand-written) |
| kafka | `streams/kafka-api.yaml` | `KafkaAdminApiDelegateImpl` |
| kinesis | `streams/kinesis-api.yaml` | `KinesisAdminApiDelegateImpl` |

Inspect codegen maps domain types (`TopicRef`, `ProtocolInspectCapabilities`, `MessageSearchRequest`, `UnifiedMessage`) via `importMappings` in `eventore-api-codegen/pom.xml`.

Pulsar admin operations use `pulsar-client-admin` (in addition to `pulsar-client`) for `PulsarAdmin` at runtime.

| URL | Description |
|-----|-------------|
| http://localhost:8080/swagger-ui.html | Swagger UI (per-stream YAML + runtime) |
| http://localhost:8080/api/v1/openapi/catalog | Which stream contracts are active in this deployment |
| http://localhost:8080/openapi/streams/kafka-api.yaml | Kafka-only contract |
| http://localhost:8080/openapi/streams/diagnostics-api.yaml | Diagnostics contract |
| `openapi/README.md` | Regeneration and frontend client steps |

See `docs/TESTING.md` for unit, integration, and CI commands.

Regenerate after contract changes:

```bash
mvn -pl eventore-api-codegen generate-sources
mvn -pl eventore-server -am package
```

List loaded provider modules: `GET /api/v1/providers`

## Adding a new stream provider

1. Create `eventore-provider-<id>/` with connector + inspector packages.
2. Add `*StreamProviderAutoConfiguration` and AutoConfiguration.imports.
3. Add dependency to `eventore-server/pom.xml` (optional for custom distros).
4. Extend `ProtocolType` in `eventore-core` and `openapi/eventore-api.yaml`.
