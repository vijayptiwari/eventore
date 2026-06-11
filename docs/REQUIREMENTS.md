# Eventore Requirements Backlog (Post-MVP)

Generated from **functionality-requirements-analyst** (evidence-based analysis after commit `c002e14`) and **business-analyst** (value scoring for the next wave).

**Date:** 2026-06-11  
**Supersedes:** Pre-MVP `REQUIREMENTS.md` / completed items in `EPICS.md`

---

## Executive Summary

MVP epics **EPIC-1 through EPIC-6** are shipped: secured deployments, inspector parity, operator diagnostics, connection wizard, and MQTT/Pulsar CI integration.

**Next highest-value themes:**

1. **Production persistence & HA safety** (BVS 90) — in-memory connections and `replicaCount: 2` in readonly Helm are a production footgun
2. **Regression test gates** (BVS 86) — SSE ownership, auth, and MCP contract tests lack CI enforcement at the HTTP layer
3. **Brief D completion** (BVS 79) — protocol-specific MCP tools for RabbitMQ/GCP/Azure agents
4. **Contract hardening** (BVS 74) — typed OpenAPI for inspect/diagnostics + audit expansion

**Recommended next step:** Hand Rank #1–#3 business briefs + P0/P1 REQs to **feature-epic-planner** for Wave 2 epics.

---

## MVP Completed (Do Not Re-Plan)

| REQ | Title | Evidence |
|-----|-------|----------|
| REQ-1 | MCP SSE consume URL + auth | `mcp/eventore-mcp/src/eventore-client.ts`, `tools-sse-contract.test.ts` |
| REQ-2 | Helm security configuration | `deploy/helm/eventore/`, `verify-security-template.sh` |
| REQ-3 | Frontend API token UX | `ApiTokenSettingsDialog.tsx`, `AppLayout.tsx` |
| REQ-5 | Kinesis shard inspection UI | `StreamInspectorShardsTab.tsx`, `kinesisListShards()` |
| REQ-6 | Kinesis admin capability metadata | `StreamProviderDescriptorFactory.java` |
| REQ-7 | MCP generic inspect + Kinesis tools | `mcp/eventore-mcp/src/tools.ts` (28 tools) |
| REQ-10 | Capability-driven inspector tabs | `hasInspectFeature()`, `StreamInspector.tsx` |
| REQ-11 | RabbitMQ queue-centric UI | `StreamInspector.tsx`, `INSPECTOR_PARITY.md` |
| REQ-12 | GCP Pub/Sub subscription/backlog | `GcpPubSubMessagingInspector.java` |
| REQ-13 | Azure Service Bus subscription/peek | `AzureServiceBusMessagingInspector.java` |
| REQ-19 | Graceful 501 inspect UX | `InspectNotSupportedError`, `GlobalExceptionHandler` |
| Brief A | Operator diagnostics | `DiagnosticsController`, `DashboardPage.tsx`, Grafana JSON |
| Brief C | Connection wizard | `ConnectionWizardDialog.tsx` |
| REQ-8 (partial) | MQTT + Pulsar Testcontainers + CI | `MqttConnectorIntegrationTest`, `PulsarConnectorIntegrationTest` |

---

## Capability Map (As Implemented Today)

| Area | Status | Evidence |
|------|--------|----------|
| 8 stream providers | Implemented | `backend/eventore-provider-*` |
| Control / data plane | Implemented | `ControlPlaneController`, connector SPI |
| Connection CRUD | **Partial** | In-memory `ConnectionRegistry` — lost on restart |
| Publish/subscribe | Implemented | WS + SSE; `SecretRefs` for credentials |
| API token auth | Implemented | Backend, frontend, Helm, MCP SSE |
| Inspector parity | Implemented | `docs/INSPECTOR_PARITY.md`; honest 501s |
| Diagnostics | Implemented | `DiagnosticsController` — **not in OpenAPI** |
| Kafka admin | Implemented | Full OpenAPI + UI + MCP |
| Kinesis shards | Implemented | Admin API + UI + MCP |
| MCP toolkit | **Partial** | Generic inspect; no protocol-dedicated RabbitMQ/GCP/Azure tools |
| Integration tests | **Partial** | 4/8 protocols in CI (`docs/TESTING.md`) |
| Playwright E2E | **Partial** | Mocked scaffold in `frontend/e2e/` |
| Audit logging | **Partial** | `AuditService.publish()` only |
| Network policy | **Partial** | Fixed broker ports; no TLS variants documented |
| Durable storage | Missing | `ConnectionRegistry` unchanged |
| Enterprise OIDC | Missing | API token only |
| HA multi-replica | **Risk** | `values-readonly.yaml` sets `replicaCount: 2` without shared state |

---

## Business-Prioritized Portfolio (Next Wave)

| Rank | Feature | BVS | Verdict | Rationale |
|------|---------|-----|---------|-----------|
| **1** | Durable connections + HA safety | **90** | Pursue (MVP slice) | Blocks real production deploys; readonly overlay already suggests 2 replicas |
| **2** | HTTP/WS/SSE regression tests | **86** | Pursue | Protects shipped security fixes; no `@SpringBootTest` on stream layer |
| **3** | Brief D — protocol MCP toolkit | **79** | Pursue (MVP slice) | AI/agent differentiation; generic inspect insufficient for cloud ops playbooks |
| **4** | MCP tests in CI | **77** | Pursue | Low effort; prevents SSE/auth regressions |
| **5** | Typed OpenAPI + diagnostics spec | **74** | Pursue (MVP slice) | Contract stability for UI/MCP/codegen |
| **6** | JMS integration test | **70** | Pursue (MVP slice) | 5/8 broker coverage; Artemis container is tractable |
| **7** | Audit expansion | **68** | Defer | Value after persistence; compliance buyers |
| **8** | Live-stack Playwright E2E | **62** | Defer | High CI cost; HTTP tests give faster ROI |
| **9** | Cloud emulator integration tests | **58** | Spike first | LocalStack/GCP/Azure emulator cost vs mock inspector tests |
| **10** | Enterprise OIDC | **55** | Defer | API token sufficient for OSS v1 |
| **11** | Pulsar/RabbitMQ admin OpenAPI | **52** | Defer | After inspect parity closure |
| **12** | Rate limiting | **48** | Defer | Premature without multi-tenant demand |

---

## Enriched Feature Briefs (Top 3)

### Brief E — Production persistence & deployment safety

**Verdict:** Pursue (MVP slice) · **BVS:** 90

**Problem:** Platform operators lose all connection profiles on pod restart. The readonly Helm overlay sets `replicaCount: 2` while subscriptions and connections are process-local.

**Outcome:** Operators can restart or scale Eventore without re-entering broker credentials; documentation prevents unsafe HA configs.

**Personas:** Platform operator (K8s), SRE on-call

**Success metrics:**
- KPI-1: Connection profiles survive controlled pod restart when persistence enabled
- KPI-2: Zero documented incidents from `replicaCount > 1` without sticky sessions (Helm NOTES warning)
- KPI-3: Time to restore after deploy ↓ 80% vs manual re-entry

**MVP scope:** Optional K8s Secret/ConfigMap-backed connection store; credentials as `env:`/`file:` refs only; Helm warning when `replicaCount > 1`; ADR for HA options

**Out of scope:** Full JDBC multi-tenant DB, cross-region replication, OIDC

**Opportunity cost:** Delays Brief D MCP tools and OpenAPI typing by ~1 sprint

---

### Brief F — Stream layer regression test gate

**Verdict:** Pursue · **BVS:** 86

**Problem:** Critical security paths (SSE `connectionId` ownership, API token on WS/SSE, inspect policy gates) ship without HTTP-level regression tests.

**Outcome:** CI blocks merges that re-break secured streaming or inspect authorization.

**Personas:** Maintainer, security-conscious operator

**Success metrics:**
- KPI-1: MockMvc/WebSocket tests cover SSE 403 (wrong/missing `connectionId`)
- KPI-2: 401 without token when auth enabled
- KPI-3: Inspect endpoints reject when `BROWSE_DESTINATIONS` disallowed

**MVP scope:** `eventore-server` `@SpringBootTest` or WebTestClient for stream + inspect smoke; no full Testcontainers stack required

**Out of scope:** Live Playwright against real brokers

---

### Brief G — Protocol-native MCP toolkit (Brief D completion)

**Verdict:** Pursue (MVP slice) · **BVS:** 79

**Problem:** Agents must chain generic `eventore_inspect_*` tools; no curated playbooks for RabbitMQ queue depth, GCP subscription backlog, or Azure peek sampling.

**Outcome:** Integration engineers and AI agents diagnose cloud/queue issues in fewer tool calls.

**Success metrics:**
- KPI-1: ≥3 dedicated tools per RabbitMQ, GCP_PUBSUB, AZURE_SERVICE_BUS
- KPI-2: `eventore://capability-matrix` MCP resource mirrors `INSPECTOR_PARITY.md`
- KPI-3: MCP `npm test` in CI

**MVP scope:** Dedicated tools + prompts; capability gating; CI test job

**Out of scope:** Full admin mutations via MCP (topic create, queue purge)

---

## New Requirements Backlog

### P0 — Production blockers

#### REQ-29: Durable connection profile storage

- **Type:** Gap
- **Priority:** P0
- **Area:** `backend/eventore-server` / `ConnectionRegistry`
- **As implemented today:** Profiles stored in `ConcurrentHashMap` (`ConnectionRegistry.java`); explicit in-memory design.
- **Problem / opportunity:** Pod restarts lose connections; multi-replica deploys cannot share state.
- **Requirement:** The system shall support optional persistent connection storage (K8s Secret/ConfigMap or volume) with credentials persisted only as `env:`/`file:` secret refs.
- **Acceptance criteria:**
  - [ ] Connections survive process restart when persistence enabled
  - [ ] Plaintext credentials rejected at save time
  - [ ] READONLY deployment mode can read persisted profiles
- **Dependencies / notes:** REQ-33 (HA design); Brief E
- **Business link:** Rank #1

#### REQ-30: Multi-replica deployment safety

- **Type:** Hardening
- **Priority:** P0
- **Area:** `deploy/helm/eventore`
- **As implemented today:** `values-readonly.yaml` sets `backend.replicaCount: 2` and `frontend.replicaCount: 2`; SSE ownership and subscriptions are single-process.
- **Problem / opportunity:** Operators deploy HA-looking configs that break streaming and connection state.
- **Requirement:** Helm shall document constraints and emit warnings when `replicaCount > 1` without persistence/sticky sessions.
- **Acceptance criteria:**
  - [ ] `NOTES.txt` warns on multi-replica without documented affinity/persistence
  - [ ] Default overlays use `replicaCount: 1` until REQ-29/33 land
  - [ ] Deployment guide section on HA limitations
- **Dependencies / notes:** REQ-33
- **Business link:** Rank #1

---

### P1 — High

#### REQ-31: Server streaming and inspect HTTP/WS regression tests

- **Type:** Test
- **Priority:** P1
- **Area:** `backend/eventore-server`
- **As implemented today:** No `@SpringBootTest` for `StreamSseController`, `StreamWebSocketHandler`, or `InspectApiDelegateImpl` HTTP behavior.
- **Problem / opportunity:** Shipped SSE ownership and auth fixes lack automated regression guard.
- **Requirement:** The test suite shall include HTTP/WS tests for SSE `connectionId` ownership, API token 401, and inspect policy gates.
- **Acceptance criteria:**
  - [ ] SSE returns 403 when `connectionId` does not own subscription
  - [ ] WS/SSE reject unauthenticated requests when token configured
  - [ ] Inspect endpoints enforce deployment mode policy
- **Dependencies / notes:** Brief F

#### REQ-32: Expand broker integration test matrix (JMS + cloud spike)

- **Type:** Test
- **Priority:** P1
- **Area:** `backend/eventore-provider-jms`, CI
- **As implemented today:** 4 protocols in CI per `docs/TESTING.md`; JMS/Kinesis/GCP/Azure skipped with documented rationale.
- **Problem / opportunity:** JMS connector lacks integration proof; cloud protocols rely on manual testing.
- **Requirement:** Add JMS Testcontainers round-trip test; document or spike cloud emulator strategy for Kinesis/GCP/Azure.
- **Acceptance criteria:**
  - [ ] `JmsConnectorIntegrationTest` with `@Tag("integration")`
  - [ ] CI runs JMS module or documents skip with issue link
  - [ ] `docs/TESTING.md` updated with emulator decision
- **Dependencies / notes:** Docker in CI

#### REQ-33: HA architecture spike

- **Type:** Spike
- **Priority:** P1
- **Area:** `backend` + `deploy`
- **As implemented today:** Single-replica assumptions throughout `SubscriptionManager`, `ConnectionRegistry`, SSE pump.
- **Problem / opportunity:** Cannot safely scale without design for sticky sessions vs externalized state.
- **Requirement:** Produce ADR covering `replicaCount > 1` options: ingress sticky sessions, external connection store, subscription affinity.
- **Acceptance criteria:**
  - [ ] ADR or architecture doc section with recommended pattern
  - [ ] Helm values template for chosen pattern
  - [ ] Explicit blockers list for full HA
- **Dependencies / notes:** REQ-29

---

### P2 — Medium

#### REQ-34: Strongly typed OpenAPI inspect + diagnostics schemas

- **Type:** Enhancement
- **Priority:** P2
- **Area:** `backend/openapi`
- **As implemented today:** `inspect-api.yaml` returns generic `type: object`; `DiagnosticsController` absent from OpenAPI bundle.
- **Requirement:** Add structured schemas from `InspectModels` and `diagnostics-api.yaml` stream; regenerate delegates.
- **Acceptance criteria:**
  - [ ] Swagger shows typed inspect models
  - [ ] Diagnostics paths documented in OpenAPI
  - [ ] Codegen compiles without breaking delegates

#### REQ-35: Expand audit logging beyond publish

- **Type:** Hardening
- **Priority:** P2
- **Area:** `backend/eventore-server`
- **As implemented today:** `AuditService` covers publish; connection CRUD and provider register unaudited.
- **Requirement:** Emit structured audit events for connection create/update/delete and control-plane register/deregister.
- **Acceptance criteria:**
  - [ ] Audit lines contain no credential values
  - [ ] Event types documented in deployment guide

#### REQ-36: Helm network policy egress completeness

- **Type:** Hardening
- **Priority:** P2
- **Area:** `deploy/helm/eventore/templates/networkpolicy.yaml`
- **As implemented today:** Fixed ports (9092, 1883, etc.); no AMQPS 5671 / Kafka 9094; cloud HTTPS guidance missing.
- **Requirement:** Configurable TLS broker ports and documented cloud egress when policy enabled.
- **Acceptance criteria:**
  - [ ] Values for extra broker ports
  - [ ] `helm template` succeeds with `networkPolicy.enabled=true`

#### REQ-37: MCP package tests in CI

- **Type:** Test
- **Priority:** P2
- **Area:** `mcp/eventore-mcp`, `.github/workflows/publish-artifacts.yml`
- **As implemented today:** `npm test` exists locally; workflow builds MCP image but does not run tests.
- **Requirement:** CI shall run `npm ci && npm test` in `mcp/eventore-mcp` on every PR.
- **Acceptance criteria:**
  - [ ] PR fails on SSE URL/auth contract regression
  - [ ] Job documented in `docs/TESTING.md`

#### REQ-38: Live-backend Playwright E2E smoke (optional CI job)

- **Type:** Test
- **Priority:** P2
- **Area:** `frontend/e2e`
- **As implemented today:** All routes mocked via `e2e/fixtures.ts`.
- **Requirement:** Optional Docker-gated job: Testcontainers Kafka + backend + one unmocked user flow.
- **Acceptance criteria:**
  - [ ] Documented in `docs/TESTING.md`
  - [ ] Gated on Docker availability

#### REQ-39: Brief D — protocol-specific MCP toolkit

- **Type:** Enhancement
- **Priority:** P2
- **Area:** `mcp/eventore-mcp`
- **As implemented today:** Generic `eventore_inspect_*` tools; no `eventore_rabbitmq_*`, `eventore_gcp_*`, `eventore_azure_*` dedicated tools or capability-matrix resource.
- **Requirement:** Add ≥3 dedicated tools per RabbitMQ, GCP_PUBSUB, AZURE_SERVICE_BUS; protocol prompts; `eventore://capability-matrix` resource.
- **Acceptance criteria:**
  - [ ] README matches `INSPECTOR_PARITY.md`
  - [ ] Tools respect `inspectCapabilities` / `adminProtocols`
  - [ ] Contract tests for new tools
- **Dependencies / notes:** REQ-37; Brief G

#### REQ-40: Diagnostics MCP tool

- **Type:** Enhancement
- **Priority:** P2
- **Area:** `mcp/eventore-mcp`
- **As implemented today:** `GET /api/v1/diagnostics/subscriptions` exists; no MCP equivalent.
- **Requirement:** Add `eventore_diagnostics_subscriptions` tool returning subscription health snapshot for agents.
- **Acceptance criteria:**
  - [ ] Tool uses same auth/SSE patterns as existing MCP client
  - [ ] Documented in README

---

### P3 — Low / deferred

| REQ | Title | Type | Notes |
|-----|-------|------|-------|
| REQ-41 | MQTT/JMS inspect depth | Enhancement | Queue depth, retained messages without destructive consume |
| REQ-42 | In-app Grafana / metrics link | Enhancement | Dashboard card linking to subscription-health dashboard |
| REQ-43 | Connection profile import/export | Enhancement | Redacted export with secret refs only |
| REQ-44 | DeploymentMode doc alignment | Consistency | Resolve `PUBLISHED` vs `ADMIN\|DEV\|READONLY` in stale docs |
| REQ-45 | Rate limiting | Hardening | Publish, inspect search, subscribe per connection |
| REQ-46 | Pulsar/RabbitMQ admin OpenAPI | Gap | Follow `kafka-api.yaml` pattern when admin features added |
| REQ-47 | Split ingress CORS/WS docs | Hardening | Split `apiBaseUrl` / `wsUrl` Helm guidance |
| REQ-48 | Frontend OpenAPI drift check | Test | CI compare hand-written types vs backend spec |
| REQ-49 | Enterprise OIDC/SAML | Gap | Deferred; API token sufficient for OSS v1 |

---

## Open Questions

1. **HA priority:** Should `values-readonly.yaml` revert to `replicaCount: 1` immediately, or is 2-replica readonly intentional for a specific customer?
2. **Persistence backend:** K8s Secret per connection vs single encrypted ConfigMap vs external DB?
3. **Cloud CI:** Budget for LocalStack Kinesis / Pub/Sub emulator vs mock-only inspector tests?
4. **Brief D scope:** Are generic inspect tools enough for GCP/Azure agents, or are dedicated tool names required?
5. **OIDC timeline:** Any enterprise prospect in next 2 quarters?

---

## Recommended Next Steps

| Step | Action | Agent |
|------|--------|-------|
| 1 | Spike REQ-33 + fix Helm footgun (REQ-30) | feature-developer |
| 2 | Plan Brief E epic (REQ-29 persistence MVP) | feature-epic-planner |
| 3 | Plan Brief F epic (REQ-31 HTTP tests) | feature-epic-planner |
| 4 | Parallel: REQ-37 MCP CI (quick win) | feature-developer |
| 5 | Plan Brief G epic (REQ-39 MCP toolkit) | feature-epic-planner |
| 6 | Archive completed items in `EPICS.md` or add `EPICS-WAVE2.md` | feature-epic-planner |

---

## Pipeline

```
Post-MVP analysis (this document)
    → feature-epic-planner (Wave 2 epics)
    → feature-developer ↔ feature-acceptance-tester
    → ci-merge-steward
```
