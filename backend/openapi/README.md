# Eventore OpenAPI (per-stream)

Contracts are split by integration surface so each stream provider can evolve independently.

| Stream | Spec | Generated Java | Delegate |
|--------|------|----------------|----------|
| **core** | `streams/core-api.yaml` | `com.eventore.api.generated.core.*` | `CoreApiDelegates` |
| **inspect** | `streams/inspect-api.yaml` | `com.eventore.api.generated.inspect.*` | `InspectApiDelegateImpl` |
| **diagnostics** | `streams/diagnostics-api.yaml` | *(hand-written REST)* | `DiagnosticsController` |
| **kafka** | `streams/kafka-api.yaml` | `com.eventore.api.generated.kafka.*` | `KafkaAdminApiDelegateImpl` |
| **kinesis** | `streams/kinesis-api.yaml` | `com.eventore.api.generated.kinesis.*` | `KinesisAdminApiDelegateImpl` |

Shared schemas: `common/schemas.yaml`

Inspect codegen reuses domain types where possible (`TopicRef`, `ProtocolInspectCapabilities`, `MessageSearchRequest`, `UnifiedMessage`) via `importMappings` in `eventore-api-codegen/pom.xml`. Delegate implementations must match generated return types.

## Regenerate server API

```bash
cd backend
mvn -pl eventore-api-codegen generate-sources
mvn -pl eventore-server -am package
```

## Regenerate frontend client

```bash
cd frontend
npm run generate:api
```

Uses the merged spec at `backend/openapi/eventore-api-bundled.yaml` (produced by the generate script).

## Runtime

- Catalog: `GET /api/v1/openapi/catalog` — specs available for the current deployment
- Per-stream YAML: `GET /openapi/streams/{stream}-api.yaml`
- Swagger UI: `/swagger-ui.html` (runtime Springdoc + static stream specs)
