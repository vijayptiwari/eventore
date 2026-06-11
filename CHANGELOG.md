# Changelog

All notable changes to the Eventore project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

#### Wave 2 — persistence, HA, security tests, JMS CI, MCP toolkit
- **Durable connection profiles**: optional JSON file persistence (`eventore.connections.persistence`) with `env:`/`file:` credential validation; Helm volume wiring when enabled.
- **HA guidance**: `docs/HA.md`, Helm NOTES multi-replica warning, `values-readonly.yaml` backend `replicaCount: 1`.
- **Security regression tests**: `StreamSseControllerTest` (SSE ownership 403), `ApiTokenSecurityIntegrationTest` (401 without token, diagnostics auth).
- **JMS integration**: `JmsConnectorIntegrationTest` against Artemis Testcontainers; CI matrix includes `eventore-provider-jms`.
- **MCP Wave 2**: protocol-specific RabbitMQ/GCP/Azure tools, `eventore_diagnostics_subscriptions`, `eventore://capability-matrix` resource, inspection prompts; `build-mcp` CI job.
- **Audit expansion**: connection CRUD and provider register/deregister events logged via `AuditService`.

#### Wave 3 — production persistence, OpenAPI, security gate, E2E CI
- **Helm PVC persistence**: `volumeType: pvc|emptyDir`, optional PVC template, NOTES on durability semantics.
- **Ingress session affinity**: optional nginx cookie affinity when `backend.replicaCount > 1`.
- **OpenAPI diagnostics stream**: `diagnostics-api.yaml`, typed `ProtocolInspectCapabilities` and diagnostics DTOs in catalog.
- **Complete REQ-31**: SSE/WebSocket 401 tests, READONLY policy HTTP test, `InspectApiDelegateImplTest`.
- **Audit subscribe/validate/inspect**: `AuditService` wired in `SubscriptionManager`, validate, inspect search.
- **NetworkPolicy**: configurable `extraBrokerPorts` for TLS broker egress.
- **MCP**: MQTT/JMS/Pulsar protocol tools + prompts; README refresh; Helm `EVENTORE_API_TOKEN` secret wiring.
- **Playwright CI**: mocked wizard + diagnostics smoke in `build-frontend-e2e` job.
- **Docs**: `TESTING.md` 5-protocol matrix, `CLOUD-CI-SPIKE.md`, `EPICS-WAVE3.md`.

#### Backend tests (per-stream JUnit 5 suite)
- **83+ unit tests** across `eventore-core` and all 8 stream providers (`Kafka`, `MQTT`, `JMS`, `Pulsar`, `RabbitMQ`, `Kinesis`, `GCP Pub/Sub`, `Azure Service Bus`).
- Shared `StreamTestFixtures` helper in each module for consistent `ConnectionProfile` construction.
- Core tests: `SubscribeDestinationsTest`, `CloudClientSupportTest`, `SubscriptionKeysTest`.
- Server tests: `LiveViewFilterTest`, `DeploymentModePolicyTest`, `GlobalExceptionHandlerTest`, `SubscriptionManagerTest`.
- `spring-boot-starter-test` added to `eventore-core` and all provider modules.

#### Frontend tests (Vitest)
- Vitest + jsdom test harness under `frontend/src/test/setup.ts`.
- `sessionCookies.test.ts` — persistence round-trip, active stream id, corrupt payload handling.
- `client.test.ts` — `canAction`, protocol defaults, URL encoding for connection IDs with special characters.
- `exportData.test.ts` — filename sanitization.

### Fixed

#### Security & integrity
- **SSE subscription hijacking**: `/api/v1/stream/{subscriptionId}` now requires `connectionId` query param and verifies ownership via `SubscriptionManager.ownsSubscription`.
- **Subscription key prefix collision**: all connectors use `SubscriptionKeys.belongsToConnection()` instead of `startsWith(connectionId)` to avoid closing wrong subscriptions (e.g. `conn-1` vs `conn-10`).
- **RabbitMQ destructive inspect search**: message search uses `reject_requeue_true` ack mode instead of `amqp` (which consumed messages from queues).
- **Azure Service Bus message locks**: processor now calls `ctx.complete()` after successful handling to prevent infinite redelivery.
- **Global publish size limit**: `eventore.max-publish-bytes` (default 10 MB) enforced in all deployment modes; DEV mode uses the stricter of global and dev limits.
- **Inspect capabilities endpoint**: now requires `BROWSE_DESTINATIONS` policy (was unauthenticated relative to other inspect endpoints).
- **UnsupportedOperationException**: mapped to HTTP 501 via `GlobalExceptionHandler` instead of generic 500.

#### Encoding & validation
- **Kafka partition header**: invalid `partition` header throws `IllegalArgumentException` instead of uncaught `NumberFormatException`.
- **RabbitMQ publish**: null payload no longer causes NPE (empty byte array used).
- **Azure publish**: honors `entityType` from request headers, not only connection profile.
- **LiveViewFilter**: regex compiled once per live view session (ReDoS mitigation); max regex length 512 chars; invalid syntax returns clear errors.
- **Frontend API client**: all connection-scoped paths use `encodeURIComponent(connectionId)`.
- **Frontend session cookies**: cookie name escaped in RegExp lookup.

#### Capability honesty (inspector `capabilities()` aligned with implementation)
- Removed advertised but unimplemented features: RabbitMQ `queue-purge`, Azure/GCP/Kinesis `message-search`, Pulsar `topic-create`/`topic-delete`, GCP `subscriptions`/`backlog`.

### Changed
- SSE subscribe response `sseUrl` now includes `?connectionId=` for authorized streaming.
- `DeploymentModePolicyTest` moved to standard Maven path `src/test/java/` and updated for `ControlPlaneRegistry` dependency.

### Roadmap delivery (P0–P3 implemented)

All items from the production-readiness audit roadmap are now implemented:

#### P0 — Security
- **API token authentication** (`ApiTokenFilter`): set `eventore.security.api-token` to require a token on every API request via `Authorization: Bearer <token>`, `X-API-Key` header, or `token` query parameter (for SSE/WebSocket clients). Comparison is constant-time; `/actuator/health` stays open for probes. Empty token (default) keeps auth disabled for local dev.
- **Configurable CORS/WebSocket origins**: `eventore.security.allowed-origins` (comma-separated, default `*`) now drives both the MVC CORS mapping and the WebSocket allowed-origin patterns. A WebSocket handshake interceptor rejects un-authenticated upgrades with 401 when a token is configured.

#### P1 — Integration & encoding
- **Kinesis shard listing implemented**: `GET /connections/{id}/kinesis/streams/{name}/shards` now returns real shards (id, hash key range, sequence number range) with pagination, 404 for unknown streams, and 502 for upstream failures — previously HTTP 501.
- **Binary payload support** via new `PayloadCodec` in `eventore-core`, wired into **all 8 connectors**:
  - Outbound: `contentType` containing `base64` (e.g. `application/base64`) decodes the payload to raw bytes before publishing; invalid base64 is rejected with a clear error.
  - Inbound: bytes that are not valid UTF-8 are base64-encoded and flagged with `contentType=application/base64`, so binary messages survive the string transport instead of being corrupted.
  - JMS sends base64 payloads as `BytesMessage` and decodes inbound `BytesMessage` bodies.
- **Testcontainers integration tests** (`@Tag("integration")`, auto-skipped without Docker):
  - `KafkaConnectorIntegrationTest` — validate, text round-trip, binary base64 round-trip, listDestinations against a real Kafka container.
  - `RabbitMqConnectorIntegrationTest` — validate (plain and `amqp://` URLs), queue round-trip, binary round-trip against a real RabbitMQ container.
  - Excluded from default `mvn test` via `excludedTestGroups=integration`; run with `mvn test -DexcludedTestGroups=`.

#### P2 — Robustness, E2E & CI
- **RabbitMQ URI-based broker URL parsing** (`RabbitMqBrokerUrls`): accepts `host`, `host:port`, `amqp://`, and `amqps://` (TLS enabled automatically, default ports 5672/5671); used by both connector and management-API inspector (with optional `managementTls` property).
- **Cloud credential fallback hardening**: AWS and GCP connectors log a warning when falling back to ambient credentials (default chain / ADC), and profiles can forbid it entirely with property `allowDefaultCredentials=false`.
- **Playwright E2E scaffold** (`frontend/e2e/`): smoke specs for dashboard, connections, browse, and navigation with fully mocked API routes; `npm run test:e2e` with auto-started Vite dev server.
- **CI test gates**: backend job now runs `mvn -B package` with unit tests (was `-DskipTests`); new `backend-integration-tests` job runs the Testcontainers suites on Docker-enabled runners and gates image publishing; frontend job runs `npm test` before building.

#### P3 — Secrets
- **Externalized credential references** (`SecretRefs`, resolved in `ConnectionProfile.credential()`):
  - `env:NAME` — reads the secret from an environment variable.
  - `file:/path` — reads the trimmed file content (Kubernetes secrets, Vault agent / CSI driver mounts).
  - Plaintext values still work; `vault:` references fail fast with guidance to mount via `file:`.

### Test commands

```bash
# Backend unit tests (all streams + core + server)
cd backend
mvn test

# Backend including Testcontainers broker tests (requires Docker)
mvn test -DexcludedTestGroups=

# Frontend unit tests
cd frontend
npm install
npm test

# Frontend E2E (requires npx playwright install chromium once)
npm run test:e2e
```

### Production deployment notes

```yaml
# application.yaml — recommended non-dev settings
eventore:
  deployment-mode: READONLY            # or ADMIN/DEV as appropriate
  max-publish-bytes: 1048576
  security:
    api-token: ${EVENTORE_API_TOKEN}   # enables auth on all API/WS endpoints
    allowed-origins: https://console.example.com
```
Credentials in connection profiles should use `env:` / `file:` references instead of plaintext.

---

## [0.1.0-SNAPSHOT] — prior work

- Multi-protocol streaming platform (8 providers) with Spring Boot 3.3 / Java 21 backend and React/Vite frontend.
- WebSocket live view, SSE subscriptions, OpenAPI surfaces per stream.
- Deployment modes: `ADMIN`, `DEV`, `READONLY`.
