# Eventore deployment

## Three pods (three workloads)

Production-style Eventore runs as **three separate containers/pods**:

| # | Workload | Image | Role |
|---|----------|-------|------|
| 1 | **Backend** | `eventore/backend` | Spring API, WebSocket, broker connectors (Kafka, MQTT, JMS, Pulsar, RabbitMQ) |
| 2 | **Frontend** | `eventore/frontend` | React UI (nginx) — talks to backend via Ingress `/api` and `/ws` |
| 3 | **MCP** (optional) | `eventore/mcp` | AI agent MCP over HTTP — talks to backend API only, not brokers directly |

```text
                    ┌─────────────────┐
  Users / Browser ─►│ Frontend :80    │
                    └────────┬────────┘
                             │ /api, /ws
                    ┌────────▼────────┐
  AI agents (MCP) ─►│ Backend :8080   │────► Kafka, MQTT, JMS, Pulsar, RabbitMQ
                    └────────▲────────┘
                             │ REST
                    ┌────────┴────────┐
                    │ MCP :3100       │  (optional 3rd pod)
                    └─────────────────┘
```

MCP is **optional**: skip the third pod if you only need the web console.

## Helm (two charts → three pod types)

| Chart | Pods | Install |
|-------|------|---------|
| [`helm/eventore`](helm/eventore) | **2** — `*-backend`, `*-frontend` | Required |
| [`helm/eventore-mcp`](helm/eventore-mcp) | **1** — `*-mcp` | Optional |

### Console only (2 pods)

```bash
helm install eventore deploy/helm/eventore -f deploy/helm/eventore/values-dev.yaml
```

### Full stack (3 pods)

```bash
# 1–2: backend + frontend
helm install eventore deploy/helm/eventore -f deploy/helm/eventore/values-dev.yaml

# 3: MCP — point at backend Service name from first release
helm install eventore-mcp deploy/helm/eventore-mcp \
  --set eventore.apiUrl=http://eventore-backend:8080/api/v1
```

Replace `eventore-backend` with `<release-name>-backend` if your Helm release name is not `eventore`.

### Three deployment *modes* (not three pods)

Admin / Dev / ReadOnly are **configuration overlays** on the **backend** (and shared Ingress), not extra pods:

- `values-admin.yaml` → `deploymentMode: ADMIN`
- `values-dev.yaml` → `deploymentMode: DEV`
- `values-readonly.yaml` → `deploymentMode: READONLY`

## Docker Compose (3 services locally)

```bash
docker compose -f docker/docker-compose.stack.yml up -d
```

- UI: http://localhost:8088  
- API: http://localhost:8080  
- MCP: http://localhost:3100/health  
