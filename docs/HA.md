# High Availability & Multi-Replica Guidance

EventOre **0.2.1** supports **single-replica** backends by default. Multi-replica deploys require **PVC persistence** and **ingress session affinity** (Wave 3 Pattern B).

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
- Suitable for dev, readonly triage dashboards, and production without HA requirements
- Use vertical scaling before adding replicas

### Pattern B — Sticky sessions + durable persistence (Wave 3 MVP HA)

1. Enable connection persistence with a **PVC** (not `emptyDir`):

   ```yaml
   eventore:
     connections:
       persistence:
         enabled: true
         volumeType: pvc          # emptyDir | pvc
         filePath: /data/connections.json
         size: 1Gi
         # existingClaim: my-rwx-claim   # optional; for pre-provisioned RWX volumes
   ```

2. When `backend.replicaCount > 1`, enable ingress session affinity:

   ```yaml
   backend:
     replicaCount: 2
   ingress:
     sessionAffinity:
       enabled: true
       cookieName: eventore-affinity
   ```

3. Accept that validation history and in-flight subscriptions remain pod-local until Pattern C.

**Semantics:** `emptyDir` survives container restart inside a pod only. `pvc` survives pod delete/recreate when the claim is retained. A shared file volume does not coordinate concurrent writers. JDBC connection persistence is available, but does not distribute live subscriptions. See the connection-store guide before choosing storage.

### Pattern C — Distributed subscriptions (unfinished)

- File, JDBC and CRD-directory connection stores exist. The CRD store uses a directory of manifests, not a live Kubernetes API controller.
- Shared subscription routing and cross-pod SSE/WebSocket delivery are **not implemented**.
- Connection persistence alone does not provide active-active streaming or failover.

## Helm checklist

- [ ] `replicaCount: 1` unless Pattern B is fully configured
- [ ] Read `NOTES.txt` after install for multi-replica warnings
- [ ] Set `volumeType: pvc` when persistence must survive pod reschedule
- [ ] Enable `ingress.sessionAffinity` when `replicaCount > 1` and using nginx ingress
- [ ] Do not enable PDB with `minAvailable: 1` on 2 replicas without understanding SSE stickiness
- [ ] When `networkPolicy.enabled`, add TLS broker ports via `networkPolicy.extraBrokerPorts` (e.g. `5671`, `9094`)

## Blockers for full HA

1. No distributed subscription registry
2. No cross-pod SSE fan-out
3. No leader-elected connection writer for file persistence on RWO volumes shared across pods
4. No OIDC/session layer for operator identity across replicas

**Next backlog:** Wave 4 items in `docs/REQUIREMENTS.md` (REQ-61+, live E2E, OIDC).

## 0.3.0 development status

The new subscription distribution interface and local bus do not complete Pattern C. RedisSubscriptionDistributionBus currently serializes frames and dispatches locally; no Redis client, network subscription or shared subscription registry exists. Selecting REDIS now fails startup explicitly instead of reporting a working cluster. JWT and workspace metadata also do not provide tenant isolation. Keep single-replica deployments as the supported default.
