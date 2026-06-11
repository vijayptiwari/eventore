# Eventore Wave 3 Epics

**Status:** COMPLETE  
**Source:** `docs/REQUIREMENTS.md` (post–Wave 2, REQ-50+)  
**Date:** 2026-06-11

## Planning Summary

- **Epics:** 6 | **Features:** 22 | **Open questions:** 5 (PVC access mode, cloud CI budget, OpenAPI delegate migration, Playwright cadence, OIDC timeline)

## Delivery Sequence

| Order | Epic | Features |
|-------|------|----------|
| 1 | EPIC-12 | PVC persistence + ingress session affinity |
| 2 | EPIC-13 | OpenAPI diagnostics + typed inspect |
| 3 | EPIC-14 | Complete stream security test gate |
| 4 | EPIC-15 | Docs sync, MCP Helm auth, README |
| 5 | EPIC-16 | Playwright CI smoke |
| 6 | EPIC-17 | Audit expansion, network policy TLS, MQTT/JMS/Pulsar MCP |

---

## Epic EPIC-12: Production-Trustworthy Persistence & HA

**Outcome:** Persistence enabled in Helm survives pod lifecycle; multi-replica SSE has affinity template.

| Feature | REQ | AC summary |
|---------|-----|------------|
| FEAT-12.1 PVC volume option | REQ-50 | `volumeType: pvc` replaces default `emptyDir` |
| FEAT-12.2 Existing claim support | REQ-50 | `existingClaim` for RWX volumes |
| FEAT-12.3 Ingress session affinity | REQ-54 | Cookie affinity when `replicaCount > 1` |

---

## Epic EPIC-13: Contract-First OpenAPI

**Outcome:** Diagnostics and inspect shapes documented in OpenAPI catalog.

| Feature | REQ | AC summary |
|---------|-----|------------|
| FEAT-13.1 diagnostics-api.yaml | REQ-51 | Subscriptions + validation history paths |
| FEAT-13.2 Typed inspect schemas | REQ-51 | `ProtocolInspectCapabilities`, `TopicRef` refs |
| FEAT-13.3 Catalog + springdoc | REQ-51 | Stream in catalog and Swagger UI |

---

## Epic EPIC-14: Complete Security Regression Gate

**Outcome:** REQ-31 fully closed.

| Feature | REQ | AC summary |
|---------|-----|------------|
| FEAT-14.1 SSE 401 without token | REQ-52 | Auth-enabled profile |
| FEAT-14.2 WebSocket handshake 401 | REQ-52 | Upgrade request rejected |
| FEAT-14.3 Deployment mode HTTP policy | REQ-52 | READONLY blocks connection create |

---

## Epic EPIC-15: Docs & MCP Operator UX

| Feature | REQ | AC summary |
|---------|-----|------------|
| FEAT-15.1 TESTING.md sync | REQ-53 | 5-protocol CI matrix |
| FEAT-15.2 MCP Helm API token | REQ-55 | `EVENTORE_API_TOKEN` from secret |
| FEAT-15.3 MCP README refresh | REQ-56 | Tools/prompts/resources accurate |

---

## Epic EPIC-16: Playwright CI Smoke

| Feature | REQ | AC summary |
|---------|-----|------------|
| FEAT-16.1 Wizard smoke | REQ-57 | Open wizard from connections page |
| FEAT-16.2 Diagnostics card smoke | REQ-57 | Dashboard subscription health visible |
| FEAT-16.3 CI job | REQ-57 | `npm run test:e2e` on PR |

---

## Epic EPIC-17: Hardening & MCP Parity

| Feature | REQ | AC summary |
|---------|-----|------------|
| FEAT-17.1 Audit subscribe/validate/inspect | REQ-59 | Structured AUDIT log lines |
| FEAT-17.2 NetworkPolicy TLS ports | REQ-60 | `extraBrokerPorts` values |
| FEAT-17.3 MQTT/JMS/Pulsar MCP tools | REQ-63 | ≥2 tools per protocol |
| FEAT-17.4 Cloud CI spike doc | REQ-58 | `docs/CLOUD-CI-SPIKE.md` decision record |

---

## Deferred (Wave 4)

REQ-61 live Playwright E2E, REQ-62 frontend OpenAPI drift CI, REQ-64 MCP HTTP integration test, REQ-65–72 P3 items.
