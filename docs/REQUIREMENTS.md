# Eventore Requirements Backlog (Post–Wave 2)

Generated from **functionality-requirements-analyst** (evidence-based analysis after commit `99f608a`) and **business-analyst** (value scoring for Wave 3).

**Date:** 2026-06-11  
**Supersedes:** Post-MVP `REQUIREMENTS.md` / completed items in `EPICS-WAVE2.md`

---

## Executive Summary

Wave 2 epics **EPIC-7 through EPIC-11** are shipped: HA safety guidance, optional file-backed connection persistence, SSE/API-token regression tests, JMS Testcontainers CI, and the protocol-native MCP toolkit.

**Highest-value themes for Wave 3:**

1. **Production-trustworthy persistence** (BVS 92) — code path exists but Helm still mounts `emptyDir`; multi-replica without PVC/RWX or sticky sessions is a split-brain risk
2. **Contract stability** (BVS 85) — `DiagnosticsController` and inspect responses are untyped in OpenAPI; UI/MCP rely on hand-written types
3. **Security test completeness** (BVS 82) — REST auth and SSE 403 ship; WS/SSE token rejection and inspect policy HTTP tests remain open
4. **Cloud broker CI strategy** (BVS 76) — 5/8 protocols proven in CI; Kinesis/GCP/Azure need emulator spike or documented mock-only stance
5. **Operator UX polish** (BVS 68) — docs drift, MCP README stale, Playwright scaffold unused in CI

**Recommended next step:** Hand Rank #1–#3 business briefs + P0/P1 REQs to **feature-epic-planner** for Wave 3 epics.

---

## Wave 2 Completed (Do Not Re-Plan)

| REQ | Title | Evidence |
|-----|-------|----------|
| REQ-29 | Durable connection profile storage | `ConnectionProfilePersistence.java`, `ConnectionRegistry.java` |
| REQ-30 | Multi-replica deployment safety | `NOTES.txt`, `values-readonly.yaml` backend `replicaCount: 1` |
| REQ-31 (partial) | SSE ownership 403 + REST API token tests | `StreamSseControllerTest.java`, `ApiTokenSecurityIntegrationTest.java` |
| REQ-32 (partial) | JMS Testcontainers + CI | `JmsConnectorIntegrationTest.java`, `publish-artifacts.yml` |
| REQ-33 | HA architecture ADR | `docs/HA.md` |
| REQ-35 (partial) | Audit connection CRUD + provider lifecycle | `AuditService.java`, `CoreConnectionsApiDelegateImpl.java`, `ControlPlaneCoordinator.java` |
| REQ-37 | MCP package tests in CI | `build-mcp` job in `publish-artifacts.yml` |
| REQ-39 | Protocol-specific MCP toolkit | `protocol-tools.ts`, `capability-matrix.ts`, `prompts.ts` |
| REQ-40 | Diagnostics MCP tool | `eventore_diagnostics_subscriptions` in `protocol-tools.ts` |
| Brief E (MVP slice) | File persistence + secret-ref validation | `ConnectionProfilePersistence.validatePersistableCredentials` |
| Brief F (MVP slice) | HTTP security regression gate | Server tests above |
| Brief G (MVP slice) | RabbitMQ/GCP/Azure MCP tools + prompts | `protocol-tools.ts`, `protocol-tools-contract.test.ts` |

---

## Capability Map (As Implemented Today)

| Area | Status | Evidence |
|------|--------|----------|
| 8 stream providers | Implemented | `backend/eventore-provider-*` |
| Control / data plane | Implemented | `ControlPlaneController`, connector SPI |
| Connection CRUD | **Partial** | Optional JSON persistence; Helm uses `emptyDir` when enabled |
| Publish/subscribe | Implemented | WS + SSE; `SecretRefs` for credentials |
| API token auth | Implemented | Backend, frontend, Helm, MCP SSE |
| Inspector parity | Implemented | `docs/INSPECTOR_PARITY.md`; honest 501s |
| Diagnostics REST | Implemented | `DiagnosticsController` — **not in OpenAPI** |
| Kafka admin | Implemented | Full OpenAPI + UI + MCP |
| Kinesis shards | Implemented | Admin API + UI + MCP |
| MCP toolkit | **Partial** | Rabbit/GCP/Azure dedicated tools; MQTT/JMS/Pulsar still generic inspect |
| Integration tests | **Partial** | **5/8** protocols in CI (Kafka, RabbitMQ, MQTT, Pulsar, JMS) |
| Playwright E2E | **Partial** | Mocked scaffold in `frontend/e2e/`; not in CI |
| Audit logging | **Partial** | Publish + connection CRUD + provider lifecycle; subscribe/inspect/validate unaudited |
| Network policy | **Partial** | Fixed plaintext ports; no TLS variants (5671, 9094, etc.) |
| HA multi-replica | **Risk** | Warnings shipped; no PVC/RWX or ingress sticky sessions |
| Enterprise OIDC | Missing | API token only |
| Docs accuracy | **Drift** | `TESTING.md`, `INSPECTOR_PARITY.md` still describe 4-protocol CI |

---

## Business-Prioritized Portfolio (Wave 3)

| Rank | Feature | BVS | Verdict | Rationale |
|------|---------|-----|---------|-----------|
| **1** | PVC/RWX persistence + HA ingress pattern | **92** | Pursue (MVP slice) | Code promises durability; `emptyDir` loses data on pod reschedule |
| **2** | Typed OpenAPI (inspect + diagnostics) | **85** | Pursue | Contract stability for UI codegen, MCP, and external integrators |
| **3** | Complete stream security test gate | **82** | Pursue | WS/SSE 401 and inspect policy HTTP tests close REQ-31 |
| **4** | Documentation sync + MCP README | **78** | Pursue | Low effort; prevents operator/agent confusion post-Wave 2 |
| **5** | MCP Helm backend auth wiring | **75** | Pursue | Secured backend deploys break MCP without manual token env |
| **6** | Cloud emulator integration spike | **76** | Spike first | 3/8 protocols untested in CI; emulator cost vs mock-only decision |
| **7** | Playwright CI smoke (mocked) | **72** | Pursue (MVP slice) | Wizard + diagnostics flows untested at UI layer |
| **8** | Audit expansion (subscribe/inspect) | **70** | Defer | Connection audit shipped; compliance value after persistence hardening |
| **9** | Network policy TLS ports | **68** | Defer | Needed when operators enable policy in TLS environments |
| **10** | Live-stack Playwright E2E | **62** | Defer | High CI cost; complete HTTP tests first |
| **11** | MQTT/JMS/Pulsar dedicated MCP tools | **60** | Defer | Generic inspect sufficient for OSS v1; cloud tools already shipped |
| **12** | Enterprise OIDC | **55** | Defer | API token sufficient for OSS v1 |
| **13** | Rate limiting | **48** | Defer | Premature without multi-tenant demand |

---

## Enriched Feature Briefs (Top 3)

### Brief H — Production-trustworthy persistence & HA patterns

**Verdict:** Pursue (MVP slice) · **BVS:** 92

**Problem:** Wave 2 added file persistence, but Helm mounts `emptyDir` when persistence is enabled. Data survives container restart inside a pod, not pod deletion, node drain, or multi-replica deploys. Operators may believe connections are durable when they are not.

**Outcome:** Operators who enable persistence get data that survives pod lifecycle events; HA path documents PVC/RWX + sticky ingress as Pattern B from `docs/HA.md`.

**Personas:** Platform operator (K8s), SRE on-call

**Success metrics:**
- KPI-1: Connection profiles survive controlled pod delete/recreate when persistence + PVC enabled
- KPI-2: Helm values document `emptyDir` vs PVC trade-off explicitly
- KPI-3: Optional ingress `sessionAffinity` template for SSE/WS when `replicaCount > 1`

**MVP scope:** Helm PVC option replacing `emptyDir`; values comments; optional ingress affinity annotations

**Out of scope:** Cross-region replication, external JDBC store, full active-active HA

**Opportunity cost:** Delays OpenAPI typing by ~0.5 sprint

---

### Brief I — Contract-first OpenAPI for inspect & diagnostics

**Verdict:** Pursue · **BVS:** 85

**Problem:** `inspect-api.yaml` returns generic `type: object` for all inspect responses. `DiagnosticsController` is hand-written REST absent from the OpenAPI bundle. Frontend maintains manual types in `client.ts`; MCP and future SDKs drift.

**Outcome:** Swagger, codegen, and frontend `generate:api` reflect real inspect and diagnostics shapes; breaking API changes are caught in CI.

**Personas:** Maintainer, integration engineer, agent tool author

**Success metrics:**
- KPI-1: Diagnostics paths appear in bundled OpenAPI catalog
- KPI-2: Inspect responses reference structured schemas from `InspectModels` / DTOs
- KPI-3: `mvn package` and frontend codegen succeed without delegate breakage

**MVP scope:** `diagnostics-api.yaml` stream; typed inspect response schemas; regenerate delegates where safe

**Out of scope:** Full admin OpenAPI for Pulsar/RabbitMQ

---

### Brief J — Complete stream-layer security regression gate

**Verdict:** Pursue · **BVS:** 82

**Problem:** Wave 2 added REST 401 tests and SSE 403 ownership test. REQ-31 acceptance criteria still require WS/SSE rejection without token and inspect endpoints enforcing deployment mode at the HTTP layer.

**Outcome:** CI blocks merges that re-break secured streaming or inspect authorization across all transports.

**Personas:** Maintainer, security-conscious operator

**Success metrics:**
- KPI-1: WebSocket handshake or first frame returns 401/close when token required and missing
- KPI-2: SSE stream URL without `token` query param returns 401 when auth enabled
- KPI-3: Inspect endpoint returns 403 in READONLY when `BROWSE_DESTINATIONS` disallowed

**MVP scope:** `@SpringBootTest` or WebTestClient additions in `eventore-server`; no new Testcontainers

**Out of scope:** Live Playwright against real brokers

---

## New Requirements Backlog

### P0 — Production blockers

#### REQ-50: Helm PVC for connection persistence (replace emptyDir)

- **Type:** Hardening
- **Priority:** P0
- **Area:** `deploy/helm/eventore/templates/backend-deployment.yaml`
- **As implemented today:** When `eventore.connections.persistence.enabled=true`, volume is `emptyDir: {}` — not durable across pod reschedule.
- **Problem / opportunity:** Operators enable persistence believing profiles survive deploys; data is lost on pod delete.
- **Requirement:** Helm shall support PVC (or existing claim) for `/data` when persistence enabled, with `emptyDir` as explicit dev-only fallback via values flag.
- **Acceptance criteria:**
  - [ ] `values.yaml` documents `persistence.volumeType: emptyDir|pvc`
  - [ ] PVC template creates or references claim when `pvc` selected
  - [ ] `helm template` succeeds for both modes
  - [ ] Deployment guide explains durability semantics
- **Dependencies / notes:** REQ-29 (code path); Brief H
- **Business link:** Rank #1

---

### P1 — High

#### REQ-51: Typed OpenAPI inspect + diagnostics schemas

- **Type:** Enhancement
- **Priority:** P1
- **Area:** `backend/openapi`, `DiagnosticsController`
- **As implemented today:** `inspect-api.yaml` uses generic objects; diagnostics REST has no OpenAPI stream.
- **Requirement:** Add `diagnostics-api.yaml` and structured inspect response schemas; bundle in server OpenAPI catalog.
- **Acceptance criteria:**
  - [ ] `/api/v1/diagnostics/subscriptions` documented with response schema
  - [ ] Inspect endpoints reference typed models (not bare `object`)
  - [ ] Codegen compiles; existing delegates unchanged or migrated safely
- **Dependencies / notes:** Brief I; former REQ-34

#### REQ-52: Complete REQ-31 — WS/SSE auth + inspect policy HTTP tests

- **Type:** Test
- **Priority:** P1
- **Area:** `backend/eventore-server`
- **As implemented today:** `ApiTokenSecurityIntegrationTest` covers REST; `StreamSseControllerTest` covers SSE 403 only.
- **Requirement:** Add tests for WS/SSE 401 when auth enabled and inspect HTTP 403 under READONLY.
- **Acceptance criteria:**
  - [ ] SSE without `token` param returns 401 when `eventore.security.api-token` set
  - [ ] WebSocket rejected without token when auth enabled
  - [ ] At least one inspect endpoint returns 403 when action disallowed
- **Dependencies / notes:** Brief J

#### REQ-53: Sync testing docs with 5-protocol CI matrix

- **Type:** Consistency
- **Priority:** P1
- **Area:** `docs/TESTING.md`, `docs/INSPECTOR_PARITY.md`
- **As implemented today:** Docs list 4 protocols; JMS marked skipped; CI runs 5 modules.
- **Requirement:** Update testing and parity docs to match `publish-artifacts.yml` and Wave 2 changelog.
- **Acceptance criteria:**
  - [ ] `TESTING.md` lists Kafka, RabbitMQ, MQTT, Pulsar, JMS with container images
  - [ ] JMS removed from skip table; cloud protocols retain skip rationale
  - [ ] MCP `build-mcp` job documented

#### REQ-54: Ingress session affinity for SSE/WS (HA Pattern B)

- **Type:** Enhancement
- **Priority:** P1
- **Area:** `deploy/helm/eventore/templates/ingress.yaml`, `docs/HA.md`
- **As implemented today:** No `sessionAffinity` annotations; `HA.md` describes pattern but no Helm template.
- **Requirement:** Optional ingress annotations for cookie-based affinity when `backend.replicaCount > 1`.
- **Acceptance criteria:**
  - [ ] Values flag `ingress.sessionAffinity.enabled` with documented annotations
  - [ ] `NOTES.txt` references affinity when multi-replica
  - [ ] `helm template` renders valid ingress

#### REQ-55: MCP Helm EVENTORE_API_TOKEN wiring

- **Type:** Hardening
- **Priority:** P1
- **Area:** `deploy/helm/eventore-mcp/`
- **As implemented today:** Chart sets `eventore.apiUrl` only; no secret ref for backend API token.
- **Requirement:** MCP deployment shall accept `apiTokenExistingSecret` mirroring main Eventore chart pattern.
- **Acceptance criteria:**
  - [ ] `EVENTORE_API_TOKEN` env from secret when backend auth enabled
  - [ ] Values documented in chart README or NOTES
  - [ ] `helm template` succeeds with secret reference

#### REQ-56: MCP README post-Wave 2 refresh

- **Type:** Consistency
- **Priority:** P1
- **Area:** `mcp/eventore-mcp/README.md`
- **As implemented today:** README lists ~28 tools / 4 prompts; code has ~40 tools, 7 prompts, capability-matrix resource.
- **Requirement:** README tool/prompt/resource tables match shipped MCP surface.
- **Acceptance criteria:**
  - [ ] Protocol-specific Rabbit/GCP/Azure tools documented
  - [ ] `eventore_diagnostics_subscriptions` and `eventore://capability-matrix` listed
  - [ ] Contract test file references accurate

#### REQ-57: Playwright wizard + diagnostics smoke in CI (mocked)

- **Type:** Test
- **Priority:** P1
- **Area:** `frontend/e2e/`, `.github/workflows/publish-artifacts.yml`
- **As implemented today:** E2E scaffold exists; CI runs Vitest only.
- **Requirement:** Add Playwright job running mocked smoke for connection wizard open/save and dashboard diagnostics card.
- **Acceptance criteria:**
  - [ ] `npx playwright test` passes locally with mocks
  - [ ] CI job added (no Docker required)
  - [ ] Documented in `docs/TESTING.md`

---

### P2 — Medium

#### REQ-58: Cloud broker integration spike (Kinesis / GCP / Azure)

- **Type:** Spike
- **Priority:** P2
- **Area:** `backend/eventore-provider-kinesis`, `gcp-pubsub`, `azure-servicebus`
- **As implemented today:** Unit tests only; no Testcontainers or emulator tests.
- **Requirement:** Spike LocalStack Kinesis, Pub/Sub emulator, or Azure test namespace; document go/no-go in `TESTING.md`.
- **Acceptance criteria:**
  - [ ] Spike doc or issue with cost/parity assessment
  - [ ] At least one cloud round-trip test OR explicit mock-only decision recorded

#### REQ-59: Expand audit logging (subscribe, inspect, validate)

- **Type:** Hardening
- **Priority:** P2
- **Area:** `AuditService`, `SubscriptionManager`, inspect delegates
- **As implemented today:** Publish + connection CRUD + provider lifecycle audited.
- **Requirement:** Emit structured audit events for subscribe start/stop, inspect search, validate success/failure.
- **Acceptance criteria:**
  - [ ] No credential values in audit lines
  - [ ] Event types listed in deployment/ops guide

#### REQ-60: Helm network policy TLS broker ports

- **Type:** Hardening
- **Priority:** P2
- **Area:** `deploy/helm/eventore/templates/networkpolicy.yaml`
- **As implemented today:** Fixed ports 9092, 1883, 61616, 6650, 5672, 8080.
- **Requirement:** Configurable `networkPolicy.extraBrokerPorts` for TLS (5671, 8883, 9094, 6651) and cloud HTTPS guidance.
- **Acceptance criteria:**
  - [ ] Values accept extra port list
  - [ ] `helm template` with `networkPolicy.enabled=true` succeeds

#### REQ-61: Live-backend Playwright E2E (optional Docker job)

- **Type:** Test
- **Priority:** P2
- **Area:** `frontend/e2e`
- **As implemented today:** All routes mocked.
- **Requirement:** Optional CI job: Testcontainers Kafka + backend + one unmocked connect/validate flow.
- **Acceptance criteria:**
  - [ ] Gated on Docker availability
  - [ ] Documented in `TESTING.md`
- **Dependencies / notes:** Former REQ-38

#### REQ-62: Frontend OpenAPI drift check in CI

- **Type:** Test
- **Priority:** P2
- **Area:** `frontend/`, `backend/openapi`
- **As implemented today:** `generate:api` exists; diagnostics types hand-written.
- **Requirement:** CI compares generated types or runs codegen diff against committed artifacts after REQ-51.
- **Acceptance criteria:**
  - [ ] PR fails on undetected OpenAPI drift
- **Dependencies / notes:** REQ-51

#### REQ-63: MQTT/JMS/Pulsar dedicated MCP tools

- **Type:** Enhancement
- **Priority:** P2
- **Area:** `mcp/eventore-mcp`
- **As implemented today:** Rabbit/GCP/Azure have `protocol-tools.ts`; others use generic inspect.
- **Requirement:** Add ≥2 dedicated tools per MQTT, JMS, Pulsar with inspection prompts.
- **Acceptance criteria:**
  - [ ] Contract tests for new tools
  - [ ] README updated

#### REQ-64: MCP HTTP integration test against Testcontainers backend

- **Type:** Test
- **Priority:** P2
- **Area:** `mcp/eventore-mcp`
- **As implemented today:** Contract tests read source files; no live MCP session against backend.
- **Requirement:** Optional integration test spinning backend + MCP tool call smoke.
- **Acceptance criteria:**
  - [ ] Documented skip when Docker unavailable

---

### P3 — Low / deferred

| REQ | Title | Type | Notes |
|-----|-------|------|-------|
| REQ-65 | MQTT/JMS inspect depth | Enhancement | Queue depth, retained messages without destructive consume |
| REQ-66 | In-app Grafana / metrics link | Enhancement | Dashboard card linking to `eventore-subscription-health.json` |
| REQ-67 | Connection profile import/export | Enhancement | Redacted export with secret refs only |
| REQ-68 | DeploymentMode doc alignment | Consistency | Resolve stale `PUBLISHED` vs `ADMIN\|DEV\|READONLY` references |
| REQ-69 | Rate limiting | Hardening | Publish, inspect search, subscribe per connection |
| REQ-70 | Pulsar/RabbitMQ admin OpenAPI | Gap | Follow `kafka-api.yaml` when admin features added |
| REQ-71 | Split ingress CORS/WS docs | Hardening | Split `apiBaseUrl` / `wsUrl` Helm guidance |
| REQ-72 | Enterprise OIDC/SAML | Gap | Deferred; API token sufficient for OSS v1 |

---

## Open Questions

1. **Persistence volume:** Should default production overlay use PVC `ReadWriteOnce` (single replica) or pursue `ReadWriteMany` for rare multi-replica + shared file store?
2. **Cloud CI budget:** LocalStack Kinesis vs GCP Pub/Sub emulator vs paid test namespace — what is acceptable monthly CI cost?
3. **OpenAPI migration:** Regenerate inspect delegates from typed spec, or document-only typing first to avoid delegate churn?
4. **Playwright in CI:** Mocked smoke on every PR vs nightly live-stack job only?
5. **OIDC timeline:** Any enterprise prospect requiring SSO in the next two quarters?

---

## Recommended Next Steps

| Step | Action | Agent |
|------|--------|-------|
| 1 | Plan Brief H epic (REQ-50 PVC + REQ-54 affinity) | feature-epic-planner |
| 2 | Plan Brief I epic (REQ-51 OpenAPI) | feature-epic-planner |
| 3 | Plan Brief J epic (REQ-52 security tests) | feature-epic-planner |
| 4 | Quick wins: REQ-53 docs sync, REQ-56 MCP README | feature-developer |
| 5 | Parallel: REQ-55 MCP Helm auth | feature-developer |
| 6 | Archive Wave 2 in `EPICS-WAVE2.md` (mark complete) | feature-epic-planner |

---

## Pipeline

```
Post–Wave 2 analysis (this document)
    → feature-epic-planner (Wave 3 epics)
    → feature-developer ↔ feature-acceptance-tester
    → ci-merge-steward
```
