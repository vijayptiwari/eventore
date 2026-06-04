# Eventore — Multi-Protocol Streaming Console

Eventore connects to **Kafka**, **MQTT**, **JMS** (Artemis), **Pulsar**, **RabbitMQ**, **AWS Kinesis**, **GCP Pub/Sub**, and **Azure Service Bus**, with cloud presets (MSK, Event Hubs, OCP Strimzi, etc.). A modular backend (`eventore-server` + one Maven module per stream) exposes **OpenAPI 3** and a **React** UI with realtime streaming. Deploy to Kubernetes with Helm in **Admin**, **Dev**, or **ReadOnly** mode.

**Product documentation:** [https://vijayptiwari.github.io/eventore/](https://vijayptiwari.github.io/eventore/) (GitHub Pages from [`docs/`](docs/)). Repo: [github.com/vijayptiwari/eventore](https://github.com/vijayptiwari/eventore). Local preview: <code>npx serve docs</code>.

## Architecture

- **Backend (pod 1):** Multi-module Spring Boot 3.3 (`eventore-server` + per-protocol `eventore-provider-*` jars), OpenAPI 3, WebSocket (`/ws/stream`) + SSE fallback
- **Frontend (pod 2):** Vite + React + TypeScript, served by nginx in cluster
- **MCP (pod 3, optional):** Separate Node MCP server for AI agents — see [`mcp/eventore-mcp`](mcp/eventore-mcp/README.md)
- **Deployment:** Helm — chart `eventore` = 2 pods (backend + frontend); chart `eventore-mcp` = 1 pod. See [`deploy/README.md`](deploy/README.md)

## Published artifacts (GHCR)

GitHub Actions publishes **one backend image per stream provider** (tags `kafka`, `mqtt`, …) plus bundles (`kafka-kinesis`, `all`). Helm **`eventore.streamProviders`** selects the matching tag.

| Backend tag | Stream providers |
|-------------|------------------|
| `kafka` | KAFKA |
| `mqtt` | MQTT |
| … | (see `deploy/ci-backend-images.json`) |
| `kafka-kinesis` | KAFKA + KINESIS |
| `all` | All eight |

```bash
helm install eventore oci://ghcr.io/vijayptiwari/charts/eventore --version 0.1.0 \
  -f deploy/helm/eventore/values-stream-kafka.yaml
```

Docs: [Published images & Helm charts](https://vijayptiwari.github.io/eventore/guide/deployment.html#published-artifacts).

## Deployment modes

| Mode | Connections CRUD | Subscribe | Publish |
|------|------------------|-----------|---------|
| ADMIN | yes | yes | yes |
| DEV | yes | yes | yes (size capped) |
| READONLY | no | yes | no |

Mode is set via `eventore.deployment-mode` (Helm `eventore.deploymentMode`).

## Local development

### 1. Start test brokers

```bash
docker compose -f docker/docker-compose.brokers.yml up -d
```

### 2. Run backend

```bash
cd backend
mvn -pl eventore-server -am spring-boot:run -Dspring-boot.run.profiles=local
```

- Swagger UI: http://localhost:8080/swagger-ui.html  
- OpenAPI contract: http://localhost:8080/openapi/eventore-api.yaml  
- Provider modules: http://localhost:8080/api/v1/providers  

See [`backend/README.md`](backend/README.md) for the module layout.

### 3. Run frontend

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173

### Example Kafka connection (API)

```bash
curl -X POST http://localhost:8080/api/v1/connections \
  -H "Content-Type: application/json" \
  -d '{"name":"local-kafka","protocol":"KAFKA","brokerUrl":"localhost:9092"}'
```

## Docker images

From repository root:

```bash
docker build -f docker/Dockerfile.backend -t eventore/backend:0.1.0 .
docker build -f docker/Dockerfile.frontend -t eventore/frontend:0.1.0 .
docker build -f docker/Dockerfile.mcp -t eventore/mcp:0.1.0 .
```

## Helm install (3 pods when MCP is enabled)

**Pods 1–2** — backend + UI (`deploy/helm/eventore`):

```bash
helm install eventore deploy/helm/eventore -f deploy/helm/eventore/values-dev.yaml
# or values-admin.yaml / values-readonly.yaml for deployment mode
```

**Pod 3** — MCP (optional, `deploy/helm/eventore-mcp`):

```bash
helm install eventore-mcp deploy/helm/eventore-mcp \
  --set eventore.apiUrl=http://eventore-backend:8080/api/v1
```

Set `image.backend.repository` / `image.frontend.repository` to your registry. Broker credentials go in `secrets.brokerCredentials` (mounted as env for connection profiles).

**All 3 locally:** `docker compose -f docker/docker-compose.stack.yml up -d`

## API overview

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/config` | Deployment mode and allowed actions |
| `GET/POST/DELETE /api/v1/connections` | Connection profiles |
| `GET /api/v1/connections/{id}/destinations` | List topics/queues |
| `POST /api/v1/connections/{id}/subscribe` | Start subscription (SSE) |
| `POST /api/v1/connections/{id}/publish` | Publish message |
| `WS /ws/stream` | Realtime subscribe/unsubscribe |

## MCP for AI agents (optional)

Deploy only when agents need broker access without the UI:

```bash
cd mcp/eventore-mcp && npm install && npm run build
# Cursor: use mcp/eventore-mcp/cursor-mcp.json.example

# Or HTTP container (separate from console):
docker compose -f docker/docker-compose.mcp.yml up -d

# Or Helm:
helm install eventore-mcp deploy/helm/eventore-mcp \
  --set eventore.apiUrl=http://<eventore-backend-service>:8080/api/v1
```

## Project layout

```
backend/          Spring Boot API and connectors
frontend/         React console
docs/             Product site & user guide (GitHub Pages)
mcp/eventore-mcp/ Optional MCP server (stdio or HTTP)
deploy/helm/      Kubernetes Helm charts (eventore + eventore-mcp)
docker/           Dockerfiles and local broker compose
```

## License

Apache-2.0 (suggested — adjust as needed).
