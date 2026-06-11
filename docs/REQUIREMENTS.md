# Eventore Requirements Backlog

Generated from **functionality-requirements-analyst** (evidence-based codebase analysis) and **business-analyst** (value scoring and enriched feature briefs).

**Date:** 2026-06-11

---

## Executive Summary

Eventore is a **production-hardened multi-protocol streaming console** (8 providers, deployment modes, Helm, MCP). Recent security and capability-honesty work closed the production-readiness audit.

**Highest-value work** clusters into three themes:

1. **Production security gaps** (P0) — MCP SSE/auth and Helm token wiring block secured deployments
2. **Operator diagnostics** (BVS 88) — turn existing metrics into actionable subscription health
3. **Protocol-native inspector parity** (BVS 85) — stop forcing Kafka-shaped UX on RabbitMQ, Kinesis, Azure, GCP

**Recommended next step:** Hand Rank #1–#3 business briefs + P0 REQs to **feature-epic-planner** for epic decomposition.

---

## Capability Map (As Implemented)

| Area | Status | Evidence |
|------|--------|----------|
| Control plane | Implemented | `ControlPlaneController`, provider register/deregister |
| Connection CRUD | Partial | In-memory only — lost on restart (`ConnectionRegistry`) |
| Publish/subscribe | Implemented | All 8 connectors; WS + SSE streaming |
| Kafka admin | Implemented | Full OpenAPI + UI (`KafkaAdminPanel`) |
| Kinesis admin API | Partial | Backend shard listing exists; **no frontend/MCP** |
| MQTT/JMS/GCP/Azure inspect | Partial | Several methods throw `UnsupportedOperationException` |
| API token auth | Partial | Backend + frontend WS; **Helm/MCP not wired** |
| MCP consume (SSE) | Broken when secured | Missing `connectionId` + auth on SSE fetch |
| Integration tests | Partial | Only Kafka + RabbitMQ Testcontainers |

---

## Business-Prioritized Portfolio

| Rank | Feature | BVS | Verdict | Rationale |
|------|---------|-----|---------|-----------|
| **1** | Operator diagnostics & subscription health | **88** | Pursue (MVP) | Fast MTTR; leverages existing `MetricsService` / health |
| **2** | Protocol-native inspector parity | **85** | Pursue (MVP) | Core differentiation; Kinesis UI is low-hanging fruit |
| **3** | Guided connection onboarding wizard | **82** | Pursue (MVP) | High adoption lift; mostly UI on existing validate/presets |
| **4** | MCP multi-provider toolkit | **78** | Pursue (MVP) | Differentiator; depends on inspector/API slices |
| **5** | Enterprise OIDC + audit | 72 | Defer | API token sufficient for OSS v1 |
| **6** | Durable connection storage | 68 | Defer | Real pain; security cost high |
| **7** | HA multi-replica subscription routing | 65 | Spike first | `replicaCount: 2` without sticky sessions is a trap |
| **8** | All-provider integration tests | 64 | Pursue (MVP) | Enabler for parity work — route via quality pipeline |
| **9** | Pulsar/RabbitMQ admin parity | 58 | Defer | After inspect parity |
| **10** | Live-stack Playwright E2E | 52 | Defer | Mocked smoke adequate for now |

---

## Requirements Backlog (Evidence-Based)

### P0 — Blocking

#### REQ-1: Fix MCP SSE consume URL and auth

- **Type:** Gap
- **Priority:** P0
- **Area:** `mcp/eventore-mcp`
- **As implemented today:** `EventoreClient.consumeMessages()` fetches `/api/v1/stream/{subscriptionId}` without `connectionId` (`eventore-client.ts:239-240`), but `StreamSseController` requires `?connectionId=` and ownership check (`StreamSseController.java:49-54`). Client sends no auth headers.
- **Problem / opportunity:** `eventore_consume_messages` and `eventore_quick_probe` fail in production when SSE ownership or API token auth is enabled.
- **Requirement:** The MCP client shall use the `sseUrl` returned by subscribe (including `connectionId`) and attach the configured API token to SSE requests when auth is enabled.
- **Acceptance criteria:**
  - [ ] `consumeMessages` uses backend-provided `sseUrl` or appends required `connectionId`
  - [ ] SSE fetch includes Bearer/`X-API-Key`/`token` when `EVENTORE_API_TOKEN` is set
  - [ ] Manual test: consume works with `eventore.security.api-token` configured
- **Dependencies / notes:** Align with frontend WS token pattern in `StreamWorkspaceContext.tsx:161-162`
- **Business link:** Blocks Rank #4 (MCP toolkit) in secured deployments

#### REQ-2: Helm chart security configuration

- **Type:** Gap
- **Priority:** P0
- **Area:** `deploy/helm/eventore`
- **As implemented today:** ConfigMap sets deployment mode and protocols only (`configmap.yaml`); no `eventore.security.api-token` or `allowed-origins` despite CHANGELOG production guidance.
- **Problem / opportunity:** Helm installs leave API/WS unauthenticated unless operators manually patch Spring config.
- **Requirement:** The Helm chart must expose `eventore.security.apiToken` and `eventore.security.allowedOrigins` (via Secret + ConfigMap/SPRING_APPLICATION_JSON) and document recommended ADMIN/READONLY overlays.
- **Acceptance criteria:**
  - [ ] Values + templates wire token and origins into backend pod env/JSON
  - [ ] Frontend ConfigMap can inject matching `apiToken` or documents sessionStorage workflow
  - [ ] `helm template` output shows security block when values set
- **Dependencies / notes:** Coordinate with REQ-3
- **Business link:** Prerequisite for any production/enterprise pilot

---

### P1 — High

#### REQ-3: Frontend API token configuration UX

- **Type:** Gap
- **Priority:** P1
- **Area:** `frontend`
- **As implemented today:** Token read from injected config or `sessionStorage` (`runtime.ts`); no UI to set token; Helm frontend config lacks `apiToken` field.
- **Problem / opportunity:** Secured deployments require manual browser storage setup.
- **Requirement:** The UI shall provide a settings entry to store/clear API token (session-scoped) and Helm shall optionally inject token into `frontend-config.js`.
- **Acceptance criteria:**
  - [ ] Token prompt/settings accessible from AppLayout
  - [ ] REST and WS requests use stored token
  - [ ] Helm value `frontend.env.apiToken` supported (prefer Secret reference pattern)
- **Dependencies / notes:** REQ-2

#### REQ-4: Durable connection profile storage

- **Type:** Gap
- **Priority:** P1
- **Area:** `backend/eventore-server`
- **As implemented today:** `ConnectionRegistry` is in-memory with explicit security note against disk persistence (`ConnectionRegistry.java:12-22`).
- **Problem / opportunity:** Pod restarts lose all connections; unsuitable for production operator workflows.
- **Requirement:** The system shall support optional persistent connection storage (e.g. Kubernetes ConfigMap/Secret, JDBC, or file volume) with credentials stored only via `env:`/`file:` references.
- **Acceptance criteria:**
  - [ ] Connections survive process restart when persistence enabled
  - [ ] Plaintext credentials never written to persistent store (validation enforced)
  - [ ] READONLY mode can use read-only store backend
- **Dependencies / notes:** Major design choice; fits existing `SecretRefs` model
- **Business verdict:** Defer (BVS 68) until HA spike resolves architecture

#### REQ-5: Kinesis shard inspection UI

- **Type:** Gap
- **Priority:** P1
- **Area:** `frontend`
- **As implemented today:** Backend exposes `GET .../kinesis/streams/{streamName}/shards` (`kinesis-api.yaml`, `KinesisAdminApiDelegateImpl`); frontend has **zero** `/kinesis/` API calls.
- **Problem / opportunity:** Kinesis operators cannot inspect shards from the console despite implemented API.
- **Requirement:** The stream inspector shall expose a Kinesis shards panel when `adminProtocols` or control plane includes KINESIS and the stream API is active.
- **Acceptance criteria:**
  - [ ] `api.kinesisListShards(connectionId, streamName)` added to client
  - [ ] UI tab/section shows shard id, hash range, sequence range
  - [ ] Errors surface 404/502 messages from backend
- **Dependencies / notes:** Uses existing OpenAPI contract
- **Business link:** Brief B MVP slice #1 (BVS 85)

#### REQ-6: Align Kinesis admin capability metadata

- **Type:** Consistency
- **Priority:** P1
- **Area:** `backend/eventore-server`
- **As implemented today:** `StreamProviderDescriptorFactory` sets `admin=true` only for KAFKA (`StreamProviderDescriptorFactory.java:28`); Kinesis has `/kinesis/*` routes and OpenAPI stream id `kinesis`.
- **Problem / opportunity:** Dashboard `adminProtocols` and UI cascade omit KINESIS; agents/MCP may skip Kinesis admin discovery.
- **Requirement:** Provider capabilities shall reflect actual admin API prefixes per protocol (KINESIS admin=true when kinesis-api is on classpath).
- **Acceptance criteria:**
  - [ ] `GET /config` → `controlPlane.uiCascade.adminProtocols` includes KINESIS when provider loaded
  - [ ] `StreamProviderDescriptorFactoryTest` updated
- **Dependencies / notes:** Low-risk metadata fix

#### REQ-7: MCP Kinesis and generic inspect tools

- **Type:** Gap
- **Priority:** P1
- **Area:** `mcp/eventore-mcp`
- **As implemented today:** MCP has Kafka-specific inspect/admin tools only (`tools.ts`); no `eventore_inspect_capabilities`, topics list, or Kinesis shard tools.
- **Problem / opportunity:** AI agents cannot discover protocol limits or operate non-Kafka inspect workflows through MCP.
- **Requirement:** MCP shall add tools mirroring generic inspect endpoints and Kinesis shard listing, gated by deployment capabilities.
- **Acceptance criteria:**
  - [ ] Tools: inspect capabilities, list topics, describe topic, Kinesis list shards
  - [ ] Tools return 501 body text when provider throws `UnsupportedOperationException`
  - [ ] README tool table updated
- **Dependencies / notes:** REQ-1 for auth
- **Business link:** Brief D (BVS 78)

#### REQ-8: Expand Testcontainers integration coverage

- **Type:** Test
- **Priority:** P1
- **Area:** `backend` providers
- **As implemented today:** Only `KafkaConnectorIntegrationTest` and `RabbitMqConnectorIntegrationTest` exist; CI runs those two modules only (`.github/workflows/publish-artifacts.yml:46`).
- **Problem / opportunity:** MQTT, JMS, Pulsar, Kinesis, GCP, Azure regressions ship undetected.
- **Requirement:** Add integration tests (or cloud emulator containers where feasible) for remaining providers, integrated into CI matrix incrementally.
- **Acceptance criteria:**
  - [ ] At least validate + publish/subscribe round-trip for MQTT and Pulsar
  - [ ] CI job includes new modules or documented skip rationale per provider
  - [ ] Tests tagged `@Tag("integration")` consistent with existing pattern
- **Dependencies / notes:** Cloud providers may need LocalStack/emulator or mock-heavy tests
- **Business link:** Rank #8 enabler for inspector parity

---

### P2 — Medium

#### REQ-9: Server streaming and inspect API tests

- **Type:** Test
- **Priority:** P2
- **Area:** `backend/eventore-server`
- **As implemented today:** Tests cover `WsCommand`, `StreamFrame`, `SubscriptionManager`, but not `StreamWebSocketHandler`, `StreamSseController`, or `InspectApiDelegateImpl`.
- **Problem / opportunity:** Recent SSE ownership fix (CHANGELOG) lacks regression tests at HTTP/WS layer.
- **Requirement:** Add MockMvc/WebSocket tests verifying subscribe→SSE ownership, token rejection, and inspect policy gates.
- **Acceptance criteria:**
  - [ ] SSE without `connectionId` returns 403
  - [ ] SSE with wrong `connectionId` returns 403
  - [ ] Inspect endpoints require `BROWSE_DESTINATIONS`
  - [ ] WebSocket handshake 401 when token configured and missing
- **Dependencies / notes:** Spring WebSocket test support

#### REQ-10: Frontend inspector tab gating from capabilities only

- **Type:** Consistency
- **Priority:** P2
- **Area:** `frontend`
- **As implemented today:** `StreamInspector.tsx` hardcodes `canSearch` for KAFKA/PULSAR/RABBITMQ and `canLag` for KAFKA, bypassing `capabilities.features`.
- **Problem / opportunity:** UI can expose tabs that backend returns 501 for, or hide tabs when capabilities advertise features.
- **Requirement:** Inspector tabs shall derive visibility solely from `inspectCapabilities.features` plus protocol-specific labels (e.g. Pulsar "Subscriptions").
- **Acceptance criteria:**
  - [ ] Search tab hidden when `message-search` absent from capabilities
  - [ ] Lag tab shown when `lag` or `backlog` present (not hardcoded KAFKA-only)
  - [ ] Groups tab uses `subscriptions` feature token consistently
- **Dependencies / notes:** Coordinate with inspector `capabilities()` in each provider

#### REQ-11: RabbitMQ queue-centric inspect UI

- **Type:** Enhancement
- **Priority:** P2
- **Area:** `frontend`
- **As implemented today:** RabbitMQ inspector advertises `queues`, `queue-detail`, `message-get`; UI uses Kafka-oriented "Consumer groups" tab logic.
- **Problem / opportunity:** RabbitMQ operators see irrelevant group/lag tabs or miss queue depth tooling.
- **Requirement:** For RABBITMQ, the inspector shall present queue list, detail, and depth/lag using Rabbit-specific labels and APIs.
- **Acceptance criteria:**
  - [ ] Queue list/detail primary tab for RABBITMQ
  - [ ] Message search uses non-destructive inspect path (regression guard for ack mode)
  - [ ] No consumer-group tab unless capability includes it
- **Dependencies / notes:** RabbitMQ uses HTTP management API; document `managementPort` requirement

#### REQ-12: GCP Pub/Sub subscription and backlog inspect

- **Type:** Gap
- **Priority:** P2
- **Area:** `backend/eventore-provider-gcp-pubsub`
- **As implemented today:** Capabilities limited to `cluster`, `topics`; `searchMessages` throws `UnsupportedOperationException`.
- **Problem / opportunity:** Cloud operators lack subscription health and backlog visibility in-console.
- **Requirement:** GCP inspector shall list subscriptions per topic and expose backlog/oldest-unacked-age metrics where Admin API permits.
- **Acceptance criteria:**
  - [ ] `capabilities()` advertises only implemented features
  - [ ] `listConsumerGroups` or dedicated subscription listing returns real subscription metadata
  - [ ] Unit tests with mocked Admin client
- **Dependencies / notes:** GCP credentials via `CloudClientSupport`

#### REQ-13: Azure Service Bus subscription inspect

- **Type:** Gap
- **Priority:** P2
- **Area:** `backend/eventore-provider-azure-servicebus`
- **As implemented today:** Features: `cluster`, `queues`, `topics`, `queue-detail`; `describeConsumerGroup` and `searchMessages` throw `UnsupportedOperationException`.
- **Problem / opportunity:** Topic subscription monitoring unavailable despite Service Bus admin SDK support.
- **Requirement:** Azure inspector shall list topic subscriptions and active message counts for queues/subscriptions.
- **Acceptance criteria:**
  - [ ] Subscription listing implemented for topics
  - [ ] Capabilities updated; 501 only for truly unsupported ops
  - [ ] Tests in `AzureServiceBusMessagingInspectorTest`
- **Dependencies / notes:** Uses `connectionString` credential pattern from UI

#### REQ-14: Strongly typed OpenAPI inspect schemas

- **Type:** Enhancement
- **Priority:** P2
- **Area:** `backend/openapi`
- **As implemented today:** `inspect-api.yaml` responses use generic `type: object` / arrays of object.
- **Problem / opportunity:** Codegen and frontend types cannot enforce contract; drift between providers undetected.
- **Requirement:** Inspect OpenAPI shall reference shared schemas for `ClusterInfo`, `TopicDetail`, `ConsumerGroupSummary`, `UnifiedMessage`, etc., from `InspectModels`.
- **Acceptance criteria:**
  - [ ] `inspect-api.yaml` uses `$ref` to `common/schemas.yaml` for all inspect DTOs
  - [ ] Regenerated Java/TS types compile without breaking delegates
  - [ ] Swagger UI shows structured models
- **Dependencies / notes:** May require schema additions to `common/schemas.yaml`

#### REQ-15: Expand audit logging beyond publish

- **Type:** Hardening
- **Priority:** P2
- **Area:** `backend/eventore-server`
- **As implemented today:** `AuditService` logs publish events only; Kafka admin calls use `auditService` in delegate but connection CRUD/delete unaudited.
- **Problem / opportunity:** Operators lack traceability for destructive admin and connection changes.
- **Requirement:** AUDIT logger shall record connection create/update/delete, provider register/deregister, and Kafka admin mutations with actor hint (User-Agent or token id hash).
- **Acceptance criteria:**
  - [ ] Structured AUDIT lines for MANAGE_CONNECTIONS and ADMIN_BROKER_OPS actions
  - [ ] No credential values in audit output
  - [ ] Documented log format in deployment guide
- **Dependencies / notes:** Extend `AuditService` methods

#### REQ-16: Helm network policy for cloud and broker egress

- **Type:** Hardening
- **Priority:** P2
- **Area:** `deploy/helm/eventore`
- **As implemented today:** Optional NetworkPolicy allows 443/80 globally and broker CIDRs on fixed ports; no AMQPS 5671, no explicit cloud endpoint CIDR guidance.
- **Problem / opportunity:** Enabling network policy may block MSK, Azure, GCP APIs or TLS broker ports.
- **Requirement:** Chart shall document and optionally template egress for cloud provider endpoints and common TLS messaging ports when `networkPolicy.enabled=true`.
- **Acceptance criteria:**
  - [ ] Values for additional broker ports (5671, 9094, etc.)
  - [ ] Documentation for cloud egress (HTTPS to AWS/GCP/Azure)
  - [ ] `helm template` validates with `values-admin.yaml` + networkPolicy enabled
- **Dependencies / notes:** Cluster-specific CIDRs remain operator responsibility

#### REQ-17: MCP automated test suite

- **Type:** Test
- **Priority:** P2
- **Area:** `mcp/eventore-mcp`
- **As implemented today:** No tests in MCP package (no `*.test.ts` files found).
- **Problem / opportunity:** MCP regressions (SSE URL, tool schemas) ship silently.
- **Requirement:** Add unit tests for `EventoreClient` URL construction, auth headers, and tool registration smoke tests with mocked fetch.
- **Acceptance criteria:**
  - [ ] Tests cover `consumeMessages` URL includes `connectionId`
  - [ ] CI job runs `npm test` in `mcp/eventore-mcp`
  - [ ] Fails if subscribe response `sseUrl` ignored
- **Dependencies / notes:** REQ-1

#### REQ-18: Real backend E2E smoke path

- **Type:** Test
- **Priority:** P2
- **Area:** `frontend/e2e`, CI
- **As implemented today:** Playwright tests mock all API routes (`e2e/smoke.spec.ts`, `mockApi` fixture).
- **Problem / opportunity:** UI/backend integration bugs (WS, CORS, auth) not caught in CI.
- **Requirement:** CI shall include optional E2E job spinning backend (Kafka profile) + frontend against live API, or Testcontainers-backed compose stack.
- **Acceptance criteria:**
  - [ ] At least one E2E test creates connection, browses destinations, opens stream page without mocks
  - [ ] Job gated on Docker availability similar to integration tests
  - [ ] Documented in `docs/guide/local-development.html`
- **Dependencies / notes:** Higher CI cost; may run on main only

#### REQ-19: Graceful UI handling of inspect 501 responses

- **Type:** Enhancement
- **Priority:** P2
- **Area:** `frontend`
- **As implemented today:** API client throws generic `Error` on non-OK; inspect tabs may still appear via hardcoded protocol checks.
- **Problem / opportunity:** Users see opaque errors when invoking unsupported inspect ops (MQTT groups, GCP search).
- **Requirement:** Inspector shall map EVT-1501/HTTP 501 to user-visible "not supported for this protocol" messaging and disable triggering controls proactively via capabilities.
- **Acceptance criteria:**
  - [ ] 501 responses parsed for `code` and `error` fields
  - [ ] Search/lag/groups tabs hidden when capabilities omit feature
  - [ ] Overview shows capability feature list from backend
- **Dependencies / notes:** Pairs with REQ-10

---

### P3 — Low

#### REQ-20: MQTT/JMS destination inspect depth

- **Type:** Enhancement
- **Priority:** P3
- **Area:** `backend` providers
- **Requirement:** Where broker APIs allow, MQTT shall expose retained-message indicators and JMS shall expose queue message counts without destructive consume.

#### REQ-21: High-availability Helm defaults

- **Type:** Enhancement
- **Priority:** P3
- **Area:** `deploy/helm/eventore`
- **Requirement:** Chart shall document multi-replica constraints (in-memory connections) and offer PDB/anti-affinity templates when persistence (REQ-4) is enabled.

#### REQ-22: Operator metrics dashboard surfacing

- **Type:** Enhancement
- **Priority:** P3
- **Area:** `backend` + `frontend`
- **Requirement:** Expose read-only metrics summary endpoint or embed Grafana dashboard links; optional dashboard card for active subscriptions and messages/sec by protocol.

#### REQ-23: Connection profile import/export

- **Type:** Enhancement
- **Priority:** P3
- **Area:** `backend` + `frontend`
- **Requirement:** Provide export/import of connection profiles with credentials redacted or as secret references only.

#### REQ-24: Fix DeploymentMode documentation drift

- **Type:** Consistency
- **Priority:** P3
- **Area:** `docs` + `backend`
- **Requirement:** Align enum javadoc, README, and docs with actual policy; clarify there is no PUBLISHED mode (or define it if product requires).

#### REQ-25: Rate limiting and abuse protection

- **Type:** Hardening
- **Priority:** P3
- **Area:** `backend/eventore-server`
- **Requirement:** Configurable rate limits on publish, inspect search, and subscribe creation per connection or client identity.

#### REQ-26: OpenAPI admin streams for non-Kafka protocols (future)

- **Type:** Gap
- **Priority:** P3
- **Area:** `backend/openapi`
- **Requirement:** When admin features are added per provider, each shall follow the kafka/kinesis pattern: stream YAML, codegen delegate, capability flag.

#### REQ-27: WebSocket/SSE CORS for split ingress

- **Type:** Hardening
- **Priority:** P3
- **Area:** `deploy` + `frontend`
- **Requirement:** Helm and docs shall cover split-domain `wsUrl`, `apiBaseUrl`, and matching `allowed-origins`.

#### REQ-28: Frontend OpenAPI client regeneration in CI

- **Type:** Consistency
- **Priority:** P3
- **Area:** `frontend`, CI
- **Requirement:** CI shall verify generated client matches OpenAPI bundle or regenerate and fail on diff.

---

## Enriched Feature Briefs (Business Analyst)

### Brief A — Operator Diagnostics & Subscription Health (P0, BVS 88)

**Problem:** Operators lack a single place to see *why* subscriptions fail (broker reachability, WS vs SSE path, protocol errors).

**Outcome:** Mean time to diagnose subscription issues drops materially; operators trust READONLY deployments for incident triage.

**Personas:**

- **Platform operator** — monitors multiple connections during an incident
- **Integration engineer** — validates a new topic/queue subscription
- **READONLY admin** — observe-only triage without publish/CRUD

**Success metrics:**

- **KPI-1:** Median time to identify failed subscription root cause ↓ 50%
- **KPI-2:** 100% of active subscriptions expose connection id, protocol, destination, transport (WS/SSE), last error, message count
- **KPI-3:** Prometheus scrape exposes `eventore.subscriptions.active`, per-protocol message counters, subscription error counter
- **KPI-4:** Health endpoint degrades when >N subscriptions in error state (configurable)

**MVP scope:**

- Diagnostics panel on Dashboard: active subscriptions, per-connection status, last error
- Extend `MetricsService` with subscription failures, connection validation outcomes
- Document Grafana dashboard JSON in `deploy/` or docs

**Out of scope:** Distributed tracing, broker-side lag replication, paging/Alertmanager

**Phase 2:** Connection-level SLO view; export diagnostics bundle for support

**User stories:**

1. As a **platform operator**, I want to see all active subscriptions and their last error so that I can tell broker vs. client issues without reading pod logs.
2. As a **READONLY admin**, I want deployment health and subscription counts on the dashboard so that I can use Eventore as an SRE console during incidents.
3. As an **integration engineer**, I want validation history on a connection so that I know if credentials or network changed since last success.

**Value hypothesis:** Operators will prefer Eventore over jumping between vendor consoles if triage is faster than `kubectl logs`.

**Risks:** Over-scoping into full APM; metric cardinality from per-connection labels.

---

### Brief B — Protocol-Native Inspector Parity (P1, BVS 85)

**Problem:** Inspect API uses a Kafka-shaped SPI (`consumer-groups`, `message-search`, `lag`). Cloud and queue protocols either 501 or return thin data, while the UI shows misleading tabs (e.g. Kinesis lacks shard UI despite backend shard listing API).

**Outcome:** Each protocol shows **only relevant inspector surfaces** with honest empty states; cloud users get native concepts (shards, subscriptions, peek) not Kafka jargon.

**Personas:**

- **Integration engineer** — samples messages safely per protocol
- **Platform operator** — compares backlog/lag where the broker supports it

**Success metrics:**

- **KPI-1:** Parity matrix published: for each of 8 providers, list inspect features **implemented / planned / N/A** (target: 0 advertised-but-unimplemented)
- **KPI-2:** Kinesis connections expose shard list in UI (API already shipped per CHANGELOG)
- **KPI-3:** Azure/GCP/RabbitMQ users complete message sampling without HTTP 501 in primary flows
- **KPI-4:** Frontend removes hardcoded `protocol === 'KAFKA'` tab rules; 100% capability-token driven

**MVP scope (vertical slices):**

1. **Kinesis:** Shards tab + stream detail in `StreamInspector` (REQ-5, REQ-6)
2. **RabbitMQ:** Queue-centric groups/lag labels (REQ-11)
3. **Azure Service Bus:** Peek-based sampling (REQ-13)
4. **GCP Pub/Sub:** Subscription list + backlog (REQ-12)
5. **Capability matrix** in docs + UI empty states (REQ-10, REQ-19)

**Out of scope:** Pulsar topic admin, RabbitMQ purge, full GCP IAM tooling

**Phase 2:** Pulsar subscription backlog depth; MQTT retained-message browser

**User stories:**

1. As an **integration engineer on Kinesis**, I want to list shards and hash ranges so that I can debug partition skew without AWS CLI.
2. As a **RabbitMQ operator**, I want queue depth and message peek so that I don't use Kafka "consumer group" language for queues.
3. As an **Azure user**, I want peek diagnostics so that I can inspect poison messages without removing them.

**Value hypothesis:** Multi-provider adopters stay when the second protocol "just works" in the inspector.

**Risks:** Unsafe peek/search on production queues; provider API rate limits.

---

### Brief C — Guided Connection Onboarding Wizard (P1, BVS 82)

**Problem:** Creating a working connection requires knowing preset fields, secret ref syntax (`env:`, `file:`), and a separate validate step — high friction for first-time and locked-down K8s users.

**Outcome:** New users reach first successful live stream in under 5 minutes using presets + inline validation.

**Personas:**

- **Integration engineer** — first install in dev cluster
- **Admin in locked-down deployment** — documents READONLY setup for others

**Success metrics:**

- **KPI-1:** Time from "new connection" to first successful subscribe ↓ 50%
- **KPI-2:** Connection create → validate success rate ↑ 20%
- **KPI-3:** 80% of new connections use a platform preset (not raw generic)
- **KPI-4:** Secret ref helper reduces plaintext credential creates

**MVP scope:**

- Multi-step wizard: pick preset → credentials (with `env:`/`file:` helper text) → validate → optional test publish (DEV/ADMIN) → "Open in browse"
- Surface `eventore_validate_connection` errors inline
- Link to protocol guide docs per preset

**Out of scope:** Connection import/export, Terraform provider, auto-discovery of brokers

**Phase 2:** MCP `eventore_quick_probe` parity in UI; connection templates from Helm values

**User stories:**

1. As an **integration engineer**, I want to pick "MSK" and see required fields so that I don't misconfigure IAM/regions.
2. As a **K8s admin**, I want guidance to use `file:` secret mounts so that credentials never appear in plaintext profiles.
3. As a **new user**, I want validate-before-save so that I know the broker is reachable immediately.

**Value hypothesis:** Onboarding friction is the #1 drop-off for multi-tool consoles.

**Risks:** Wizard maintenance as presets grow; DEV publish in wizard needs deployment-mode guard.

---

### Brief D — MCP Multi-Provider Toolkit (P2, BVS 78)

**Problem:** AI agents get excellent Kafka inspection/admin MCP tools but must fall back to generic REST for Pulsar, RabbitMQ, Kinesis, Azure, GCP — weakening the "unified control plane for agents" story.

**Outcome:** Agents reliably diagnose non-Kafka brokers via MCP with protocol-aware prompts and tools.

**Personas:**

- **Platform operator** using Cursor/Claude with MCP
- **SRE** automating incident runbooks

**Success metrics:**

- **KPI-1:** ≥3 new MCP tools per non-Kafka priority provider (RabbitMQ, Kinesis, GCP)
- **KPI-2:** `eventore_probe_broker` prompt success rate across protocols
- **KPI-3:** MCP README tool table matches actual backend capability matrix

**MVP scope:**

- `eventore_kinesis_list_shards`, `eventore_rabbitmq_inspect_queue`, `eventore_gcp_list_subscriptions` (names illustrative)
- Protocol-specific prompts mirroring Kafka inspection playbook
- Resources: `eventore://capability-matrix`

**Out of scope:** MCP write/admin for all providers; bi-directional broker discovery

**Depends on:** REQ-1 (auth), REQ-7 (tools), Brief B API slices

**Value hypothesis:** MCP drives adoption in AI-forward platform teams.

**Risks:** Tool sprawl; drift from OpenAPI per-stream contracts.

---

## Merged Action Plan

| Phase | Items | Focus |
|-------|-------|-------|
| Week 1 (P0) | REQ-1, REQ-2, REQ-3 | Secured production + MCP |
| Week 2–3 (P1) | REQ-5, REQ-6, REQ-10, REQ-11 | Kinesis UI + capability-driven inspector |
| Week 3–4 (P1) | Brief C (onboarding wizard) | Adoption lift |
| Week 4+ (P1) | REQ-12, REQ-13, Brief A | Cloud inspect + diagnostics |
| Parallel | REQ-8, REQ-9, REQ-17 | Test pyramid (quality-orchestrator) |
| Spike (3 days) | Rank #7 HA | Sticky SSE/WS vs single-replica doc |

---

## Open Questions (Block Epic Planning)

1. **Go-to-market wedge:** Kafka-first (MSK/Strimzi) or true multi-protocol from day one?
2. **Deployment profile:** What % of targets run `replicaCount > 1` with READONLY?
3. **Enterprise pipeline:** Any prospect requiring OIDC/SAML in the next quarter?
4. **Telemetry appetite:** Is anonymous usage telemetry acceptable for wizard funnel and inspector tab metrics?
5. **Cloud inspect depth:** For Azure/GCP, is peek/sample in Eventore sufficient or is "link to cloud console" acceptable for MVP?
6. **MCP distribution:** Is MCP primarily local (stdio/Cursor) or cluster HTTP — affects auth model?

---

## Recommended Next Agents

| Priority | Agent | Input |
|----------|-------|-------|
| **Now** | **feature-epic-planner** | Briefs A–C + REQ-1 through REQ-11 |
| **Parallel** | **quality-orchestrator** | REQ-8, REQ-9, REQ-17, REQ-18 |
| **After spike** | **feature-epic-planner** | Brief D + HA architecture decision |

---

## Value Traps to Avoid

- Building Pulsar topic admin before peek/shard/subscription basics work everywhere
- Large RBAC epic before operators can diagnose a failed subscription
- MCP tool explosion without shared capability matrix

---

## Analysis Pipeline

```
Codebase analysis (functionality-requirements-analyst)
    → Business value scoring (business-analyst)
    → Epic decomposition (feature-epic-planner)          ← recommended next
    → Implementation (feature-developer ↔ feature-acceptance-tester)
    → Merge (ci-merge-steward)
```
