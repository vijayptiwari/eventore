# Eventore Epic & Feature Plan

Generated from `docs/REQUIREMENTS.md` by **feature-epic-planner**.

**Date:** 2026-06-11

---

## Planning Summary

- **Source requirements:** 28 REQ-n items (P0–P3), 4 business briefs (A–D), merged action plan, 6 open questions
- **Epics proposed:** 6 | **Features:** 24 | **Open questions:** 6 (resolved via assumptions below)
- **In-scope planning:** P0 REQ-1/2; P1 REQ-3/5/6/7/8; Brief A, B, C; REQ-10/11/12/13/19 via Brief B
- **Deferred (rationale only):** REQ-4, REQ-9, REQ-14–18, REQ-20–28, Brief D, enterprise OIDC, HA spike, Pulsar/RabbitMQ admin parity

---

## Assumptions & Decisions

| Open question | Planning assumption |
|---------------|---------------------|
| **Q1: Go-to-market wedge** | True multi-protocol MVP — Kinesis, RabbitMQ, Azure, GCP inspector slices ship in parallel with security hardening; Kafka remains reference implementation, not exclusive wedge. |
| **Q2: Multi-replica deployments** | Default `backend.replicaCount: 1`; diagnostics and wizard target single-replica; no sticky-session work in this pass (HA spike deferred). |
| **Q3: Enterprise OIDC** | API token auth is sufficient for OSS v1; no OIDC/SAML features planned. |
| **Q4: Telemetry appetite** | No anonymous product telemetry; success metrics validated via Prometheus counters, manual QA, and optional admin-only funnel counters (no external analytics SDK). |
| **Q5: Cloud inspect depth** | In-console peek/subscription/backlog is MVP; cloud-console links allowed only as secondary empty-state CTAs, not primary flows. |
| **Q6: MCP distribution** | Primary surface is local stdio/Cursor with `EVENTORE_API_URL` + `EVENTORE_API_TOKEN`; auth mirrors frontend Bearer token on REST and SSE. |

**Normalization decisions:**

- **REQ-10 + REQ-19** merged into **FEAT-3.3** (capability-driven gating + 501 UX are one vertical slice).
- **REQ-5 + REQ-6** kept adjacent but separate: metadata (FEAT-3.1) unblocks UI gating (FEAT-3.2).
- **Brief A** has no REQ-n; traced as **Brief A** with optional alignment to REQ-22 (metrics surfacing) without planning REQ-22 in full.

---

## Open Questions (block planning until answered)

None remain blocking after assumptions above. Revisit if product chooses Kafka-only wedge, multi-replica default, or OIDC in next quarter.

---

## Epic EPIC-1: Secured Production Deployments

**Outcome:** Operators can deploy Eventore via Helm with API token auth end-to-end; MCP consume and quick-probe work when `eventore.security.api-token` is set.

**Success metrics:** `eventore_consume_messages` succeeds with token + SSE ownership; `helm install` with `values-admin.yaml` overlay yields authenticated backend without manual Spring JSON patching.

**Priority:** P0  
**Estimated size:** M  
**Affected areas:** `mcp/eventore-mcp/`, `deploy/helm/eventore/`, `frontend/src/config/`, `frontend/src/components/`

### Feature FEAT-1.1: Fix MCP SSE consume URL and auth

**Requirement traceability:** REQ-1  
**Description:** Repair `EventoreClient.consumeMessages()` to use backend-provided `sseUrl` (includes `connectionId`) and attach API token headers on SSE fetch.  
**User / system value:** AI agents and `eventore_quick_probe` work in secured deployments.  
**Complexity:** S  
**Dependencies:** None  
**Out of scope:** MCP HTTP transport server mode; REQ-17 automated MCP tests (deferred)

**Acceptance criteria:**

- [ ] **AC-1:** Given `subscribe()` returns `{ subscriptionId, sseUrl: "/api/v1/stream/{id}?connectionId={cid}" }`, when `consumeMessages(subscriptionId, …)` is called with optional `sseUrl` parameter (or subscribe response object), then fetch URL equals `baseUrl`-resolved `sseUrl` — not a reconstructed path missing `connectionId` (`eventore-client.ts:239-240` bug fixed).
- [ ] **AC-2:** Given `EVENTORE_API_TOKEN` (or client constructor token option) is set, when `consumeMessages` opens SSE, then request headers include `Authorization: Bearer <token>` (align with `StreamWorkspaceContext.tsx:161-162` WS pattern).
- [ ] **AC-3:** Given backend has `eventore.security.api-token` configured, when SSE is requested without token, then fetch returns 401/403 and `consumeMessages` throws with status in message.
- [ ] **AC-4:** Given valid token and `connectionId`, when consuming after `POST /api/v1/connections/{connectionId}/subscribe`, then at least one `MESSAGE` SSE frame is collected within timeout (`CoreSubscribeApiDelegateImpl.java:39-41` contract).
- [ ] **AC-5 (compat):** When no API token configured on either side, existing unsecured dev behavior unchanged.
- [ ] **AC-6 (tools):** `eventore_quick_probe` and subscribe-then-consume tool paths pass `sseUrl` from subscribe response to `consumeMessages`.
- [ ] **AC-7 (docs):** `mcp/eventore-mcp/README.md` documents `EVENTORE_API_TOKEN` for SSE auth.

**Implementation notes (non-binding):**

- Touchpoints: `mcp/eventore-mcp/src/eventore-client.ts`, `mcp/eventore-mcp/src/tools.ts`
- Reference: `frontend/src/stream/StreamWorkspaceContext.tsx` token query param pattern; `StreamSseController.java:48-54` ownership check
- Risks: Relative vs absolute `sseUrl` resolution when `EVENTORE_API_URL` has path prefix

---

### Feature FEAT-1.2: Helm chart security configuration

**Requirement traceability:** REQ-2  
**Description:** Expose `eventore.security.apiToken` and `eventore.security.allowedOrigins` in Helm values, wiring via Secret + `SPRING_APPLICATION_JSON`.  
**User / system value:** Production Helm installs are authenticated by default when operators set values.  
**Complexity:** M  
**Dependencies:** None (coordinates with FEAT-1.3)  
**Out of scope:** Ingress basic-auth removal; network policy egress (REQ-16 deferred)

**Acceptance criteria:**

- [ ] **AC-1:** `values.yaml` adds:
  ```yaml
  eventore:
    security:
      apiToken: ""          # prefer existingSecret
      apiTokenExistingSecret: ""
      apiTokenSecretKey: "api-token"
      allowedOrigins: "*"   # production: explicit host list
  ```
- [ ] **AC-2:** When `eventore.security.apiToken` or `apiTokenExistingSecret` is set, `helm template` renders backend `SPRING_APPLICATION_JSON` containing `"security": { "api-token": "...", "allowed-origins": "..." }` (kebab-case per Spring binding in `EventoreProperties.Security`).
- [ ] **AC-3:** When token provided via values, chart creates or references Kubernetes Secret; token never appears in ConfigMap plaintext (Secret mount or `secretKeyRef` env).
- [ ] **AC-4:** `values-admin.yaml` and `values-readonly.yaml` include commented security block with recommended overlays.
- [ ] **AC-5:** `templates/NOTES.txt` documents: set token, set matching frontend token (FEAT-1.3), verify `GET /api/v1/config` returns 401 without token when configured.
- [ ] **AC-6 (compat):** Empty `apiToken` + no existingSecret → security block omitted; dev unsecured behavior preserved.
- [ ] **AC-7 (verify):** `helm template deploy/helm/eventore -f deploy/helm/eventore/values-admin.yaml --set eventore.security.apiToken=test` shows security JSON in backend pod spec.

**Implementation notes (non-binding):**

- Touchpoints: `deploy/helm/eventore/values.yaml`, `templates/configmap.yaml`, `templates/secret.yaml`, `templates/backend-deployment.yaml`
- Patterns: existing `SPRING_APPLICATION_JSON` merge in `configmap.yaml`
- Risks: Split ingress CORS (REQ-27 deferred) may still need manual `allowedOrigins` tuning

---

### Feature FEAT-1.3: Frontend API token configuration UX

**Requirement traceability:** REQ-3  
**Description:** Settings UI to store/clear session-scoped API token; Helm injects optional `apiToken` into `frontend-config.js`.  
**User / system value:** Secured deployments need no manual `sessionStorage` DevTools setup.  
**Complexity:** M  
**Dependencies:** FEAT-1.2  
**Out of scope:** OIDC login; persistent localStorage token

**Acceptance criteria:**

- [ ] **AC-1:** AppLayout header exposes **Settings** (gear icon or nav item) opening dialog/sheet with API token password input, Save, Clear.
- [ ] **AC-2:** Given user saves token, when any `api.*` REST call runs, then `authHeaders()` sends `Authorization: Bearer <token>` (`client.ts:41-48`).
- [ ] **AC-3:** Given user saves token, when WebSocket connects, then URL includes `?token=<encoded>` per `StreamWorkspaceContext.tsx:161-163`.
- [ ] **AC-4:** Token stored in `sessionStorage` key `eventore.apiToken` (`runtime.ts:1`); Clear removes key and subsequent requests omit auth.
- [ ] **AC-5:** Injected `window.__EVENTORE_CONFIG__.apiToken` takes precedence over sessionStorage (`runtime.ts:15-24`); Helm value `frontend.env.apiToken` or `frontend.env.apiTokenExistingSecret` renders into `frontend-config.js`.
- [ ] **AC-6 (errors):** Given wrong token, when user loads Dashboard, then visible error banner: "API authentication failed (401)" with link to Settings — not silent empty state.
- [ ] **AC-7 (tests):** Extend `frontend/src/api/client.test.ts` with settings helper save/clear round-trip; existing auth header tests remain green.
- [ ] **AC-8 (Helm):** `helm template` with `frontend.env.apiTokenExistingSecret` shows token in frontend ConfigMap only when explicitly using inject pattern; document Secret reference as preferred.

**Implementation notes (non-binding):**

- Touchpoints: `frontend/src/components/AppLayout.tsx`, new `ApiTokenSettingsDialog.tsx`, `frontend/src/config/runtime.ts`, `deploy/helm/eventore/templates/configmap.yaml`
- Patterns: `PortalAboutDialog` modal pattern
- Risks: Token in frontend ConfigMap is visible to anyone with ConfigMap read — document as dev convenience; production should use session Settings workflow

---

## Epic EPIC-2: Operator Diagnostics & Subscription Health

**Outcome:** Operators diagnose subscription failures from the Dashboard without reading pod logs; Prometheus and health reflect subscription error pressure.

**Success metrics:** KPI-1–KPI-4 from Brief A — 100% active subscriptions expose id, protocol, destination, transport, last error, message count; health degrades past configurable error threshold.

**Priority:** P1  
**Estimated size:** L  
**Affected areas:** `backend/eventore-server/` (`SubscriptionManager`, `MetricsService`, `EventoreHealthIndicator`), `frontend/src/pages/DashboardPage.tsx`, `deploy/`

### Feature FEAT-2.1: Subscription diagnostics REST API

**Requirement traceability:** Brief A  
**Description:** Expose read-only snapshot of in-process subscriptions with metadata for triage.  
**User / system value:** Single API for incident dashboards and future MCP tooling.  
**Complexity:** M  
**Dependencies:** None  
**Out of scope:** Cross-replica aggregation (HA deferred)

**Acceptance criteria:**

- [ ] **AC-1:** New endpoint `GET /api/v1/diagnostics/subscriptions` returns `200` with array of:
  - `subscriptionId`, `connectionId`, `connectionName`, `protocol`, `destination`
  - `transport`: `"WS"` | `"SSE"` | `"BOTH"` (derived from active consumers)
  - `messageCount`, `lastError` (nullable string), `startedAt` (ISO-8601)
  - `status`: `"ACTIVE"` | `"ERROR"` | `"SLOW_CONSUMER"`
- [ ] **AC-2:** Given `DeploymentMode.READONLY`, when endpoint called, then `200` (observation allowed under `SUBSCRIBE` or new `BROWSE_DIAGNOSTICS` action — use existing `SUBSCRIBE` read if no new action).
- [ ] **AC-3:** Given no active subscriptions, when called, then `200` with `[]`.
- [ ] **AC-4 (security):** When API token configured and request lacks token, then `401` with `EVT-HTTP-401`.
- [ ] **AC-5 (tests):** `DiagnosticsApiDelegateImplTest` (new) covers active subscription listing after mock subscribe; `SubscriptionManager` extended to track `lastError`, `messageCount`, transport flags.
- [ ] **AC-6 (OpenAPI):** Add path to `core-api.yaml` (or `diagnostics-api.yaml` stream); regenerate delegates.

**Implementation notes (non-binding):**

- Extend `SubscriptionManager.ActiveSubscription` record with mutable diagnostic fields updated in `MessageHandler.onMessage` / `onError`
- SSE vs WS: track whether queue consumer (SSE) or WS handler attached

---

### Feature FEAT-2.2: Subscription metrics and health degradation

**Requirement traceability:** Brief A (KPI-3, KPI-4)  
**Description:** Extend `MetricsService` with error counters; degrade `EventoreHealthIndicator` when error subscriptions exceed threshold.  
**Complexity:** M  
**Dependencies:** FEAT-2.1  
**Out of scope:** Alertmanager paging; distributed tracing

**Acceptance criteria:**

- [ ] **AC-1:** Prometheus exposes:
  - `eventore.subscriptions.active` (existing)
  - `eventore.subscriptions.errors` (gauge, count in ERROR status)
  - `eventore.subscription.errors.total` (counter, incremented on each `StreamEvent.error`)
  - `eventore.connection.validations.total` with tags `result=success|failure`, `protocol=<PROTOCOL>`
- [ ] **AC-2:** `EventoreProperties` adds `diagnostics.errorSubscriptionThreshold` (default `5`); when `subscriptionsInError >= threshold`, `EventoreHealthIndicator` returns `Health.down()` with detail `subscriptionsInError`, `threshold`.
- [ ] **AC-3:** Below threshold, health remains `UP` with existing `activeSubscriptions` detail (`EventoreHealthIndicator.java:20-25`).
- [ ] **AC-4 (tests):** `MetricsServiceTest`, `EventoreHealthIndicatorTest` cover counter increment and down transition.
- [ ] **AC-5 (config):** Helm `eventore.diagnostics.errorSubscriptionThreshold` wired in `SPRING_APPLICATION_JSON`.

---

### Feature FEAT-2.3: Connection validation history

**Requirement traceability:** Brief A (user story #3)  
**Description:** Track last N validation outcomes per connection in-memory for triage.  
**Complexity:** S  
**Dependencies:** None  
**Out of scope:** Durable validation store (REQ-4 deferred)

**Acceptance criteria:**

- [ ] **AC-1:** `GET /api/v1/diagnostics/connections/{connectionId}/validations` returns last 10 entries: `timestamp`, `status` (`OK`|`FAILED`), `message` (no credentials).
- [ ] **AC-2:** Given `POST /connections/{id}/validate` succeeds or fails, when response emitted, then entry appended to ring buffer and `eventore.connection.validations.total` incremented.
- [ ] **AC-3:** Given unknown `connectionId`, then `404` with `EVT-1404`.
- [ ] **AC-4 (tests):** `CoreConnectionsApiDelegateImplTest` extended for history append on validate.

---

### Feature FEAT-2.4: Dashboard diagnostics panel UI

**Requirement traceability:** Brief A (KPI-2, user stories #1–2)  
**Description:** Dashboard card listing active subscriptions, errors, and validation summary.  
**Complexity:** M  
**Dependencies:** FEAT-2.1, FEAT-2.3  
**Out of scope:** Full APM charts; per-connection SLO (Brief A Phase 2)

**Acceptance criteria:**

- [ ] **AC-1:** `DashboardPage.tsx` adds **Subscription health** card calling `GET /api/v1/diagnostics/subscriptions` with 10s poll in incident-friendly deployments.
- [ ] **AC-2:** Table columns: Connection, Protocol, Destination, Transport, Messages, Status, Last error (truncated with tooltip).
- [ ] **AC-3:** Given `lastError` present, row styled as error; status badge `ERROR` | `ACTIVE` | `SLOW_CONSUMER`.
- [ ] **AC-4:** Empty state: "No active subscriptions — open Live Stream or Browse to start."
- [ ] **AC-5:** Connections card shows last validation time/status per connection (from FEAT-2.3) with link to Connections page.
- [ ] **AC-6 (READONLY):** Panel visible in READONLY mode (read-only triage per Brief A persona).

---

### Feature FEAT-2.5: Grafana dashboard artifact

**Requirement traceability:** Brief A (MVP scope)  
**Description:** Ship importable Grafana JSON for subscription and protocol metrics.  
**Complexity:** S  
**Dependencies:** FEAT-2.2  
**Out of scope:** Hosted Grafana in Helm subchart

**Acceptance criteria:**

- [ ] **AC-1:** `deploy/grafana/eventore-subscription-health.json` includes panels for `eventore.subscriptions.active`, `.errors`, `eventore.messages.received` by `protocol`, `eventore.subscription.errors.total` rate.
- [ ] **AC-2:** `docs/guide/deployment.html` (or README deploy section) documents import steps and required Prometheus scrape of `/actuator/prometheus`.
- [ ] **AC-3:** Dashboard variables: `deployment_mode` from `eventore_health` detail if exposed, else documented static.

---

## Epic EPIC-3: Protocol-Native Inspector Parity

**Outcome:** Each protocol shows only relevant inspector surfaces; Kinesis shards, RabbitMQ queues, Azure/GCP subscriptions work without Kafka jargon or silent 501s.

**Success metrics:** Brief B KPI-1–KPI-4; parity matrix published; zero advertised-but-unimplemented features.

**Priority:** P1  
**Estimated size:** XL  
**Affected areas:** `backend/eventore-server/controlplane/`, `backend/eventore-provider-*`, `frontend/src/components/StreamInspector*.tsx`, `docs/`

### Feature FEAT-3.1: Align Kinesis admin capability metadata

**Requirement traceability:** REQ-6  
**Description:** Set `admin=true` for KINESIS when kinesis provider loaded; include in `adminProtocols` cascade.  
**Complexity:** S  
**Dependencies:** None  
**Out of scope:** New Kinesis admin routes beyond existing shard API

**Acceptance criteria:**

- [ ] **AC-1:** `StreamProviderDescriptorFactory.capabilitiesFor()` sets `caps.setAdmin(provider.protocol() == KAFKA || provider.protocol() == KINESIS)` (`StreamProviderDescriptorFactory.java:28`).
- [ ] **AC-2:** Given Kinesis provider on classpath and enabled, `GET /api/v1/config` → `controlPlane.uiCascade.adminProtocols` includes `"KINESIS"`.
- [ ] **AC-3:** `dataPlaneApiPrefixes` still includes `/api/v1/connections/{connectionId}/kinesis` (existing line 43).
- [ ] **AC-4 (tests):** `StreamProviderDescriptorFactoryTest` asserts KINESIS `admin=true`, MQTT `admin=false`.
- [ ] **AC-5 (compat):** KAFKA-only images unchanged except test updates.

---

### Feature FEAT-3.2: Kinesis shard inspection UI

**Requirement traceability:** REQ-5, Brief B slice #1  
**Description:** Shards tab in Stream Inspector + Kinesis admin panel entry when KINESIS in `adminProtocols`.  
**Complexity:** M  
**Dependencies:** FEAT-3.1  
**Out of scope:** Kinesis iterator publish UI; MCP tool (FEAT-4.2)

**Acceptance criteria:**

- [ ] **AC-1:** `frontend/src/api/client.ts` adds `kinesisListShards(connectionId, streamName)` → `GET /connections/{id}/kinesis/streams/{streamName}/shards`.
- [ ] **AC-2:** When `protocol === 'KINESIS'` and stream selected, Inspector shows **Shards** tab listing `shardId`, `hashKeyRange`, `sequenceNumberRange` from `KinesisListShards200ResponseInner`.
- [ ] **AC-3:** Given unknown stream, when tab loads, then UI shows backend error message from `502`/`404` (`KinesisAdminApiDelegateImpl` upstream mapping) — not generic "Error".
- [ ] **AC-4:** Kafka admin tab hidden for KINESIS; Kinesis-specific admin section or tab visible when `adminProtocols` includes KINESIS.
- [ ] **AC-5 (tests):** Component test or MSW test for shards table render with mock response.
- [ ] **AC-6 (types):** Regenerate or hand-add TS types matching `kinesis-api.yaml` shard schema.

---

### Feature FEAT-3.3: Capability-driven inspector gating and 501 UX

**Requirement traceability:** REQ-10, REQ-19, Brief B KPI-4  
**Description:** Remove hardcoded protocol checks in `StreamInspector.tsx`; map HTTP 501 / `EVT-1501` to friendly empty states.  
**Complexity:** M  
**Dependencies:** None (benefits from FEAT-3.1)  
**Out of scope:** OpenAPI typed schemas (REQ-14 deferred)

**Acceptance criteria:**

- [ ] **AC-1:** `canSearch` derived only from `hasInspectFeature(feats, 'message-search')` — remove `protocol === 'KAFKA'|'PULSAR'|'RABBITMQ'` overrides (`StreamInspector.tsx:82-86`).
- [ ] **AC-2:** `canLag` from `lag` or `backlog` feature tokens only — remove `protocol === 'KAFKA'` shortcut (line 87-88).
- [ ] **AC-3:** Groups tab `show` when `subscriptions` or `queues` feature present; label from protocol map: RabbitMQ → "Queues", Pulsar → "Subscriptions", Kafka → "Consumer groups".
- [ ] **AC-4:** `client.ts` `request()` parses JSON error body `{ code, error }`; when `code === 'EVT-1501'` or status `501`, thrown `InspectNotSupportedError` (or equivalent) with `error` message preserved.
- [ ] **AC-5:** Inspector tabs catching `InspectNotSupportedError` show inline banner: "Not supported for {protocol}: {error}" — not uncaught stack.
- [ ] **AC-6:** Overview tab lists `capabilities.features` as chips from backend.
- [ ] **AC-7 (tests):** Unit test: MQTT capabilities without `message-search` → search tab hidden.

---

### Feature FEAT-3.4: RabbitMQ queue-centric inspect UI

**Requirement traceability:** REQ-11, Brief B slice #2  
**Description:** Primary queue list/detail/depth UX using RabbitMQ management API features.  
**Complexity:** M  
**Dependencies:** FEAT-3.3  
**Out of scope:** RabbitMQ purge/admin (Brief B out of scope)

**Acceptance criteria:**

- [ ] **AC-1:** For `protocol === 'RABBITMQ'`, Groups tab label "Queues" and lists queues from `inspectTopics` / queue list API (reusing topics list backed by Rabbit `listTopics`).
- [ ] **AC-2:** Queue detail shows depth (`messages`, `messages_ready`, `messages_unacknowledged`) from `describeTopic` / queue-detail feature.
- [ ] **AC-3:** Lag tab label "Queue depth" for RABBITMQ; uses `inspectLag` with queue name as `groupId` where inspector maps lag to depth.
- [ ] **AC-4:** Message search uses existing non-destructive `searchMessages` path; regression test documents ack-mode safety in `RabbitMqMessagingInspectorTest`.
- [ ] **AC-5:** No "Consumer groups" tab unless capability includes `subscriptions` (RabbitMQ should not).
- [ ] **AC-6:** Overview notes `managementPort` requirement when cluster info attributes include management error (`RabbitMqMessagingInspector.java:72-74`).

---

### Feature FEAT-3.5: GCP Pub/Sub subscription and backlog inspect

**Requirement traceability:** REQ-12, Brief B slice #4  
**Description:** Implement subscription listing and backlog metrics in `GcpPubSubMessagingInspector`.  
**Complexity:** L  
**Dependencies:** FEAT-3.3  
**Out of scope:** GCP IAM admin; message search (remains unsupported)

**Acceptance criteria:**

- [ ] **AC-1:** `capabilities()` advertises `subscriptions`, `backlog` (and retains `cluster`, `topics`); removes unimplemented `message-search`.
- [ ] **AC-2:** `listConsumerGroups(profile)` returns subscription summaries per topic: `groupId` = subscription name, `state`, `attributes` including `topic`.
- [ ] **AC-3:** `consumerLag(profile, subscription, topicFilter)` returns backlog count and `oldestUnackedAge` when Admin API provides `numUndeliveredMessages` / `oldestUnackedMessageAge`.
- [ ] **AC-4:** `searchMessages` continues to throw → mapped to `501` / `EVT-1501` via `GlobalExceptionHandler`; capabilities do not advertise search.
- [ ] **AC-5 (tests):** `GcpPubSubMessagingInspectorTest` with mocked `SubscriptionAdminClient` / `TopicAdminClient`.
- [ ] **AC-6 (UI):** GCP connections show Subscriptions + Backlog tabs via FEAT-3.3 gating without hardcoded protocol checks.

---

### Feature FEAT-3.6: Azure Service Bus subscription and peek inspect

**Requirement traceability:** REQ-13, Brief B slice #3  
**Description:** List topic subscriptions with active counts; peek-based message sampling.  
**Complexity:** L  
**Dependencies:** FEAT-3.3  
**Out of scope:** Full Service Bus admin mutations

**Acceptance criteria:**

- [ ] **AC-1:** `capabilities()` adds `subscriptions`, `message-search` (peek-only, non-destructive), `backlog` where applicable.
- [ ] **AC-2:** `listConsumerGroups` lists topic subscriptions with `activeMessageCount`, `deadLetterMessageCount` in summary attributes.
- [ ] **AC-3:** `describeConsumerGroup` returns subscription metadata without throwing for valid subscription name.
- [ ] **AC-4:** `searchMessages` implements peek (max `maxMessages` from request, default 10) using Service Bus peek API — messages not removed from entity.
- [ ] **AC-5:** Queue entities: `consumerLag` or queue detail exposes `activeMessageCount`.
- [ ] **AC-6 (tests):** `AzureServiceBusMessagingInspectorTest` covers subscription list and peek with mocked `ServiceBusAdministrationClient` / receiver.
- [ ] **AC-7 (errors):** Invalid entity → `502` / `EVT-1502` with broker message; not `EVT-1500`.

---

### Feature FEAT-3.7: Inspector parity matrix documentation

**Requirement traceability:** Brief B KPI-1  
**Description:** Published matrix of inspect features per provider.  
**Complexity:** S  
**Dependencies:** FEAT-3.1–3.6 (document final state)  
**Out of scope:** Automated matrix generation in CI

**Acceptance criteria:**

- [ ] **AC-1:** `docs/INSPECTOR_PARITY.md` table: 8 protocols × features (`cluster`, `topics`, `queues`, `subscriptions`, `lag/backlog`, `message-search`, `shards`, `admin`) with status **Implemented** | **Planned** | **N/A**.
- [ ] **AC-2:** Overview tab links to matrix anchor for unsupported empty states.
- [ ] **AC-3:** Matrix matches each provider's `capabilities()` output in codebase at release tag.

---

## Epic EPIC-4: MCP Inspect Tool Expansion

**Outcome:** AI agents discover inspect capabilities and invoke generic + Kinesis inspect tools with honest 501 handling.

**Success metrics:** REQ-7 tool list; README table matches backend capability matrix.

**Priority:** P1  
**Estimated size:** M  
**Affected areas:** `mcp/eventore-mcp/src/tools.ts`, `eventore-client.ts`, README

### Feature FEAT-4.1: Generic inspect MCP tools

**Requirement traceability:** REQ-7, Brief D (partial — core tools only)  
**Description:** Add `eventore_inspect_capabilities`, `eventore_inspect_topics`, `eventore_inspect_topic`, `eventore_inspect_search` gated by deployment.  
**Complexity:** M  
**Dependencies:** FEAT-1.1  
**Out of scope:** Brief D full toolkit (Rabbit/GCP-specific named tools deferred)

**Acceptance criteria:**

- [ ] **AC-1:** Tools registered with Zod schemas mirroring REST paths under `/connections/{id}/inspect/*`.
- [ ] **AC-2:** `eventore_inspect_capabilities` → `GET .../inspect/capabilities`.
- [ ] **AC-3:** `eventore_inspect_topics` accepts optional `filter`; `eventore_inspect_topic` requires `topic`.
- [ ] **AC-4:** Given backend returns `501` with body `{ "code": "EVT-1501", "error": "..." }`, tool returns MCP text content with status and message — not uncaught throw.
- [ ] **AC-5:** `eventore_get_config` documents which inspect tools apply per `inspectProtocols` in tool descriptions.
- [ ] **AC-6 (docs):** README tool table updated with new tools.

---

### Feature FEAT-4.2: Kinesis list shards MCP tool

**Requirement traceability:** REQ-7, REQ-5  
**Description:** `eventore_kinesis_list_shards` wrapping Kinesis admin API.  
**Complexity:** S  
**Dependencies:** FEAT-1.1, FEAT-3.1  
**Out of scope:** Rabbit/GCP MCP tools (Brief D deferred)

**Acceptance criteria:**

- [ ] **AC-1:** Tool args: `connectionId`, `streamName`; calls `GET /connections/{id}/kinesis/streams/{streamName}/shards`.
- [ ] **AC-2:** When KINESIS not in `adminProtocols`, tool description states prerequisite; runtime returns clear error from `404`/`403` config.
- [ ] **AC-3:** Successful response JSON pretty-printed with shard id and ranges.
- [ ] **AC-4:** `EventoreClient` gains `kinesisListShards()` with auth headers on REST (same as other methods after FEAT-1.1 auth pattern applied to all `request()` calls if not already).

---

## Epic EPIC-5: Guided Connection Onboarding Wizard

**Outcome:** New users reach first successful live stream in under 5 minutes using presets, inline validation, and secret-ref guidance.

**Success metrics:** Brief C KPI-1–KPI-4; 80% preset usage measurable via optional `eventore.connections.created` counter with `source=preset|manual` tag (admin metrics only).

**Priority:** P1  
**Estimated size:** L  
**Affected areas:** `frontend/src/pages/`, `frontend/src/components/`, links to `docs/guide/`

### Feature FEAT-5.1: Wizard shell and entry points

**Requirement traceability:** Brief C  
**Description:** Multi-step wizard route/modal from Connections page and Dashboard getting-started CTA.  
**Complexity:** M  
**Dependencies:** None  
**Out of scope:** Connection import/export (REQ-23 deferred)

**Acceptance criteria:**

- [ ] **AC-1:** Connections page **New connection** opens wizard (modal or `/connections/new` route) with steps: Preset → Credentials → Validate → Done.
- [ ] **AC-2:** Dashboard getting-started step 1 links to wizard.
- [ ] **AC-3:** Wizard shows progress indicator (1/4–4/4); Back/Next navigation preserves form state.
- [ ] **AC-4:** Cancel confirms discard; no orphan connection created.
- [ ] **AC-5:** READONLY mode hides wizard entry (or shows read-only message per `canAction(..., 'MANAGE_CONNECTIONS')`).

---

### Feature FEAT-5.2: Preset and credential steps

**Requirement traceability:** Brief C (user stories #1–2)  
**Description:** Preset picker with protocol guide links; credential fields with `env:`/`file:` helper.  
**Complexity:** M  
**Dependencies:** FEAT-5.1  
**Out of scope:** Auto-discovery of brokers

**Acceptance criteria:**

- [ ] **AC-1:** Step 1 lists `api.listPlatforms()` filtered by `supportedProtocols` (reuse `ConnectionsPage` preset logic).
- [ ] **AC-2:** Selecting preset pre-fills protocol, `cloudProvider`, `streamPlatform`, `brokerUrl`, `properties` per `StreamPlatformPreset`.
- [ ] **AC-3:** Step 2 shows dynamic fields from `PROTOCOL_EXTRA_FIELDS` plus credential inputs with password masking.
- [ ] **AC-4:** Inline helper text: "Use `env:VAR_NAME` or `file:/path/to/secret` for credentials" with link to deployment secrets doc.
- [ ] **AC-5:** Protocol guide link per preset (static map to `docs/guide/*.html` or in-app protocol summary from `api.listPlatforms` description).

---

### Feature FEAT-5.3: Validate-before-save step

**Requirement traceability:** Brief C (user story #3, KPI-2)  
**Description:** Validate broker reachability before persisting connection profile.  
**Complexity:** M  
**Dependencies:** FEAT-5.2  
**Out of scope:** Async background validation after save

**Acceptance criteria:**

- [ ] **AC-1:** Step 3 calls validate against ephemeral profile: `POST /connections` with draft, then `POST /connections/{id}/validate`, then on success offers Save; on failure shows inline errors from `EVT-1400` / `502` / `EVT-1502` bodies without saving (or deletes draft on abandon).
- [ ] **AC-2:** Alternative acceptable pattern: validate via dedicated validate-only endpoint if added — must not leave broken connections in registry on validation failure.
- [ ] **AC-3:** Validation errors mapped to field-level hints where backend message references broker URL, region, or credentials.
- [ ] **AC-4:** User can Retry validation without restarting wizard.
- [ ] **AC-5:** Successful validation displays green check with broker cluster id from validate response when available.

---

### Feature FEAT-5.4: Test publish and browse handoff

**Requirement traceability:** Brief C (MVP optional test publish)  
**Description:** Optional test publish in DEV/ADMIN; CTA to Browse after save.  
**Complexity:** S  
**Dependencies:** FEAT-5.3  
**Out of scope:** MCP `eventore_quick_probe` UI parity (Brief C Phase 2)

**Acceptance criteria:**

- [ ] **AC-1:** Step 4 (Done) shows connection name, protocol, validate status; primary CTA **Open in Browse** navigates to `/browse?connectionId={id}`.
- [ ] **AC-2:** When `deploymentMode` is `DEV` or `ADMIN` and protocol supports publish, optional **Send test message** button calls `api.publish` with small payload; disabled in READONLY/PUBLISHED modes.
- [ ] **AC-3:** Test publish failure shows non-blocking warning; connection remains saved.
- [ ] **AC-4:** Wizard completion invalidates `['connections']` query cache.

---

## Epic EPIC-6: Provider Integration Test Enabler

**Outcome:** CI catches regressions in MQTT and Pulsar connectors; pattern established for remaining providers.

**Success metrics:** REQ-8 acceptance; CI runs new `@Tag("integration")` tests.

**Priority:** P1 (enabler)  
**Estimated size:** L  
**Affected areas:** `backend/eventore-provider-mqtt/`, `backend/eventore-provider-pulsar/`, `.github/workflows/`

### Feature FEAT-6.1: MQTT Testcontainers integration test

**Requirement traceability:** REQ-8  
**Description:** Validate + publish/subscribe round-trip against Mosquitto/Eclipse Mosquitto container.  
**Complexity:** M  
**Dependencies:** None  
**Out of scope:** Full MQTT inspect integration

**Acceptance criteria:**

- [ ] **AC-1:** `MqttConnectorIntegrationTest` in `eventore-provider-mqtt` with `@Tag("integration")`.
- [ ] **AC-2:** Test flow: build connection profile → `validate` → `publish` → `subscribe` → receive ≥1 message within timeout.
- [ ] **AC-3:** Uses Testcontainers generic container or `hivemq/mqtt-server` image documented in test javadoc.
- [ ] **AC-4:** Skips gracefully when Docker unavailable (JUnit condition or maven profile matching Kafka test pattern).

---

### Feature FEAT-6.2: Pulsar Testcontainers integration test

**Requirement traceability:** REQ-8  
**Description:** Same round-trip for Pulsar standalone container.  
**Complexity:** M  
**Dependencies:** None  
**Out of scope:** Pulsar admin API tests

**Acceptance criteria:**

- [ ] **AC-1:** `PulsarConnectorIntegrationTest` with `@Tag("integration")` in `eventore-provider-pulsar`.
- [ ] **AC-2:** Round-trip on tenant/namespace/topic default from connector test fixtures.
- [ ] **AC-3:** `@Tag("integration")` consistent with `KafkaConnectorIntegrationTest` / `RabbitMqConnectorIntegrationTest`.

---

### Feature FEAT-6.3: CI workflow matrix expansion

**Requirement traceability:** REQ-8  
**Description:** Include MQTT and Pulsar integration modules in CI with documented skip for cloud providers.  
**Complexity:** S  
**Dependencies:** FEAT-6.1, FEAT-6.2  
**Out of scope:** Kinesis/GCP/Azure emulator tests (document skip rationale)

**Acceptance criteria:**

- [ ] **AC-1:** `.github/workflows/publish-artifacts.yml` (or dedicated integration workflow) runs `mvn -pl eventore-provider-mqtt,eventore-provider-pulsar test -Dgroups=integration` when Docker available.
- [ ] **AC-2:** `docs/TESTING.md` (or CI comment) documents skip rationale for KINESIS, GCP_PUBSUB, AZURE_SERVICE_BUS, JMS (emulator cost/complexity).
- [ ] **AC-3:** CI failure on integration test regression blocks merge (same policy as Kafka/RabbitMQ).

---

## Deferred Items (Brief Rationale Only)

| Item | Rationale |
|------|-----------|
| **REQ-4** Durable connection storage | BVS 68; security/migration cost; blocked on HA architecture spike (`replicaCount > 1` trap). |
| **REQ-9** Server streaming/inspect HTTP tests | P2; route via quality-orchestrator parallel pass, not MVP feature epics. |
| **REQ-14** Typed OpenAPI inspect schemas | P2 refactor; high churn while inspector parity in flight. |
| **REQ-15–16** Audit expansion, Helm network policy | P2 hardening after operators can diagnose subscriptions (Brief A). |
| **REQ-17** MCP automated tests | P2; depends on REQ-1 landing first; quality pipeline item. |
| **REQ-18** Live-stack Playwright E2E | P2; higher CI cost; mocked smoke adequate per portfolio rank #10. |
| **REQ-20–28** | P3 enhancements, consistency, rate limits, OIDC-adjacent — post-MVP. |
| **Brief D** MCP multi-provider toolkit beyond REQ-7 | BVS 78; depends on Brief B API slices + REQ-1; tool sprawl risk. |
| **Enterprise OIDC + audit** | BVS 72; API token sufficient for OSS v1. |
| **HA multi-replica spike** | BVS 65; in-memory connections + SSE ownership need design before PDB/anti-affinity defaults. |
| **Pulsar/RabbitMQ admin parity** | BVS 58; after inspect parity; avoid admin before peek/shard/subscription basics. |

---

## Recommended Delivery Sequence

| Order | ID | Title | Rationale |
|-------|-----|-------|-----------|
| 1 | FEAT-1.1 | Fix MCP SSE consume URL and auth | P0 blocker for secured MCP |
| 2 | FEAT-1.2 | Helm chart security configuration | P0 production prerequisite |
| 3 | FEAT-1.3 | Frontend API token UX | Completes secured deploy vertical slice with REQ-2 |
| 4 | FEAT-3.1 | Kinesis admin capability metadata | Low-risk enabler for Kinesis UI/MCP |
| 5 | FEAT-4.1 | Generic inspect MCP tools | Builds on fixed auth; agent discoverability |
| 6 | FEAT-4.2 | Kinesis list shards MCP tool | Quick win on existing API |
| 7 | FEAT-3.3 | Capability-driven inspector gating | Prevents misleading tabs before cloud work |
| 8 | FEAT-3.2 | Kinesis shard inspection UI | Brief B highest ROI UI slice |
| 9 | FEAT-3.4 | RabbitMQ queue-centric UI | Brief B slice #2; uses gating from 3.3 |
| 10 | FEAT-5.1–5.2 | Wizard shell + preset/credential steps | Adoption lift; mostly UI on existing APIs |
| 11 | FEAT-5.3–5.4 | Validate-before-save + browse handoff | Completes Brief C MVP |
| 12 | FEAT-3.5 | GCP subscription/backlog inspect | Cloud vertical slice |
| 13 | FEAT-3.6 | Azure subscription/peek inspect | Cloud vertical slice |
| 14 | FEAT-3.7 | Inspector parity matrix docs | Document honest final state |
| 15 | FEAT-2.1–2.2 | Diagnostics API + metrics/health | Brief A backend foundation |
| 16 | FEAT-2.3–2.4 | Validation history + Dashboard panel | Brief A operator UX |
| 17 | FEAT-2.5 | Grafana dashboard artifact | Brief A observability closure |
| 18 | FEAT-6.1–6.3 | MQTT/Pulsar integration tests + CI | Enabler epic; parallel from week 2 |

---

## Handoff

### Ready for development (feature-developer)

**Wave 1 — P0 security (start immediately):**
- FEAT-1.1, FEAT-1.2, FEAT-1.3

**Wave 2 — Inspector parity enablers:**
- FEAT-3.1, FEAT-3.3, FEAT-3.2, FEAT-3.4, FEAT-4.1, FEAT-4.2

**Wave 3 — Adoption:**
- FEAT-5.1, FEAT-5.2, FEAT-5.3, FEAT-5.4

**Wave 4 — Cloud inspect + diagnostics:**
- FEAT-3.5, FEAT-3.6, FEAT-3.7, FEAT-2.1, FEAT-2.2, FEAT-2.3, FEAT-2.4, FEAT-2.5

**Parallel enabler (quality pipeline):**
- FEAT-6.1, FEAT-6.2, FEAT-6.3 — recommend **quality-orchestrator** with target score 80 after implementation

### Blocked pending answers

None — all open questions resolved via documented assumptions. Escalate to product if assumptions wrong (especially Q1 wedge and Q5 cloud depth).

### Suggested next agent

| Phase | Agent | Input |
|-------|-------|-------|
| **Now** | **feature-developer** | EPIC-1 features FEAT-1.1–1.3 |
| **After Wave 1** | **feature-acceptance-tester** | P0 AC checklists + manual Helm + MCP consume test |
| **Parallel** | **quality-orchestrator** | EPIC-6 after FEAT-6.1–6.2 land |
| **Post-MVP** | **feature-epic-planner** | Brief D + REQ-4/HA spike outcomes |

---

## Pipeline

```
docs/REQUIREMENTS.md
    → docs/EPICS.md (this document)
    → feature-developer ↔ feature-acceptance-tester
    → ci-merge-steward
```
