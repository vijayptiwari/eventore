# Eventore Wave 2 Epics

**Status:** COMPLETE (commit `99f608a`)  
**Source:** `docs/REQUIREMENTS.md` (post-MVP, REQ-29+)  
**Date:** 2026-06-11

## Planning Summary

- **Epics:** 5 | **Features:** 18 | **Open questions:** 3 (persistence backend preference, cloud emulator budget, OIDC timeline)

## Delivery Sequence

| Order | Epic | Features |
|-------|------|----------|
| 1 | EPIC-7 | HA safety + ADR |
| 2 | EPIC-8 | File-backed connection persistence |
| 3 | EPIC-9 | Stream/inspect HTTP security tests |
| 4 | EPIC-10 | JMS Testcontainers + CI |
| 5 | EPIC-11 | MCP CI + Brief D tools + diagnostics tool |

---

## Epic EPIC-7: Deployment Safety & HA Guidance

**Outcome:** Operators cannot accidentally deploy broken multi-replica configs.

| Feature | REQ | AC summary |
|---------|-----|------------|
| FEAT-7.1 Helm replica warnings | REQ-30 | NOTES.txt warns when `replicaCount > 1` |
| FEAT-7.2 Readonly overlay fix | REQ-30 | `values-readonly.yaml` uses `replicaCount: 1` until persistence |
| FEAT-7.3 HA architecture ADR | REQ-33 | `docs/HA.md` with blockers and patterns |

---

## Epic EPIC-8: Durable Connection Profiles

**Outcome:** Connections survive pod restart when persistence enabled.

| Feature | REQ | AC summary |
|---------|-----|------------|
| FEAT-8.1 File persistence store | REQ-29 | JSON file load on startup, save on CRUD |
| FEAT-8.2 Secret-ref validation | REQ-29 | Reject plaintext credentials when persisting |
| FEAT-8.3 Helm persistence wiring | REQ-29 | `eventore.connections.persistence` in values + volume |

---

## Epic EPIC-9: Stream Security Regression Tests

**Outcome:** CI blocks SSE ownership and API token regressions.

| Feature | REQ | AC summary |
|---------|-----|------------|
| FEAT-9.1 SSE ownership 403 | REQ-31 | MockMvc test wrong `connectionId` |
| FEAT-9.2 API token 401 | REQ-31 | SpringBootTest without Bearer |
| FEAT-9.3 Diagnostics auth | REQ-31 | Token required when auth enabled |

---

## Epic EPIC-10: JMS Integration Coverage

**Outcome:** Fifth broker protocol proven in CI.

| Feature | REQ | AC summary |
|---------|-----|------------|
| FEAT-10.1 Artemis round-trip | REQ-32 | `JmsConnectorIntegrationTest` |
| FEAT-10.2 CI matrix | REQ-32 | Add `eventore-provider-jms` to workflow |

---

## Epic EPIC-11: MCP Toolkit Completion

**Outcome:** Agents get protocol playbooks and CI contract gates.

| Feature | REQ | AC summary |
|---------|-----|------------|
| FEAT-11.1 MCP CI job | REQ-37 | `npm test` in publish-artifacts.yml |
| FEAT-11.2 RabbitMQ/GCP/Azure tools | REQ-39 | ≥3 dedicated tools per protocol |
| FEAT-11.3 Capability matrix resource | REQ-39 | `eventore://capability-matrix` |
| FEAT-11.4 Diagnostics MCP tool | REQ-40 | `eventore_diagnostics_subscriptions` |
| FEAT-11.5 Protocol inspect prompts | REQ-39 | RabbitMQ, GCP, Azure playbooks |

---

## Deferred (Wave 3)

REQ-34 OpenAPI typing, REQ-35 audit expansion, REQ-36 network policy TLS ports, REQ-38 Playwright live E2E, REQ-41–49 P3 items.
