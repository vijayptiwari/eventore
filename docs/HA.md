# High Availability & Multi-Replica Guidance

EventOre **0.1.x** supports **single-replica** backends by default. Multi-replica deploys require **PVC persistence** and **ingress session affinity** (Wave 3 Pattern B).

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

**Semantics:** `emptyDir` survives container restart inside a pod only. `pvc` survives pod delete/recreate when the claim is retained. For true multi-replica connection sharing, use `ReadWriteMany` storage or an external store (future).

### Pattern C — Externalized State & Distributed Fan-Out (Delivered)

Pattern C enables fully horizontal, stateless multi-replica deployments:

1. **Externalized Connection Stores**:
   - `JDBC` store with PostgreSQL/relational backend and distributed optimistic locking via row versioning (`eventore.connections.store-type: JDBC`).
   - `K8S_CRD` store keeping connection profiles as Kubernetes manifests (`kind: EventoreConnection`).

2. **Distributed Subscription Registry & Message Fan-Out Bus**:
   - `SubscriptionDistributionBus` SPI broadcasts stream frames across pods over Redis Pub/Sub (`eventore:cluster:stream-fanout`).
   - Configure in `application.yml`:
     ```yaml
     eventore:
       cluster:
         mode: REDIS           # LOCAL (standalone) | REDIS (multi-replica)
         redis-host: redis-master.default.svc.cluster.local
         redis-port: 6379
         channel: eventore:cluster:stream-fanout
     ```
   - Pods subscribing to any broker automatically publish stream frames to the cluster bus; any connected WebSocket or SSE client on any pod receives the messages without ingress session affinity.
   - Automatic local fallback protects against transient Redis disconnects.

3. **Enterprise Identity & Multi-Tenancy**:
   - OIDC / OAuth2 / JWT Bearer token validation with HMAC-SHA256 verification and claim-based RBAC (`PLATFORM_ADMIN`, `WORKSPACE_ADMIN`, `OPERATOR`, `VIEWER`).
   - Multi-tenant workspace isolation with dynamic tenant CRUD and UI header switcher.

## Helm checklist

- [ ] `replicaCount: 1` when running standalone Pattern A
- [ ] For multi-replica Pattern B without Redis: set `volumeType: pvc` (RWX) and enable `ingress.sessionAffinity`
- [ ] For multi-replica Pattern C: set `eventore.cluster.mode: REDIS`, configure Redis host/port, and set `eventore.connections.storeType: JDBC`
- [ ] When `networkPolicy.enabled`, add TLS broker ports via `networkPolicy.extraBrokerPorts` (e.g. `5671`, `9094`)

## HA Status Matrix

1. **Distributed subscription registry & cross-pod fan-out**: Delivered via `RedisSubscriptionDistributionBus` SPI.
2. **Externalized connection storage**: Delivered via `JdbcConnectionProfileStore` (with optimistic locking) and `K8sCrdConnectionProfileStore`.
3. **Enterprise identity across replicas**: Delivered via `JwtTokenValidator` with OIDC/OAuth2/JWT bearer claims and multi-tenant workspaces.
