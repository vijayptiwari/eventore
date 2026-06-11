# High Availability & Multi-Replica Guidance

Eventore **0.1.x** is designed for **single-replica** backend deployments unless persistence and ingress affinity are explicitly configured.

## Current limitations

| Component | Single-replica assumption | Multi-replica impact |
|-----------|---------------------------|----------------------|
| `ConnectionRegistry` | In-memory or optional file store per pod | Each pod has isolated connection set unless shared volume + `ReadWriteMany` |
| `SubscriptionManager` | Process-local subscriptions | SSE/WS consumers attach to pod that created subscription |
| `ValidationHistoryService` | In-memory ring buffer | Per-pod history |
| SSE pump | `StreamSseController` on creating pod | Client must reach same pod or subscription not found |

## Recommended patterns

### Pattern A — Single replica (default)

- `backend.replicaCount: 1`
- Suitable for dev, readonly triage dashboards, and MVP production with persistence for connections only
- Use vertical scaling before adding replicas

### Pattern B — Sticky sessions + file persistence (MVP HA)

1. Enable `eventore.connections.persistence.enabled=true` with a **shared** volume (`ReadWriteMany`) or external store (future REQ)
2. Configure ingress **session affinity** (cookie-based) so SSE/WebSocket clients stick to one backend pod
3. Accept that validation history and in-flight subscriptions remain pod-local

### Pattern C — Externalized state (future)

- Shared connection store (DB or K8s API)
- Subscription routing via message bus or sticky subscription registry
- **Not implemented in Wave 2**

## Helm checklist

- [ ] `replicaCount: 1` unless Pattern B is fully configured
- [ ] Read `NOTES.txt` after install for multi-replica warnings
- [ ] Do not enable PDB with `minAvailable: 1` on 2 replicas without understanding SSE stickiness

## Blockers for full HA

1. No distributed subscription registry
2. No cross-pod SSE fan-out
3. No leader-elected connection writer for file persistence on RWO volumes
4. No OIDC/session layer for operator identity across replicas

**Trigger to revisit:** REQ-29 durable store + product decision on Pattern B vs C.
