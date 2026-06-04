# Eventore deployment

## Stream-provider model

Deployments are **stream-provider based**, not “full vs slim”:

1. Set **`eventore.streamProviders`** in Helm (list of protocols: `KAFKA`, `MQTT`, …).
2. The chart derives the **backend image tag** from that list (unless `image.backend.tag` is set).
3. CI publishes **one image per provider** plus explicit **bundles** in `deploy/ci-backend-images.json`.

| `streamProviders` | Image tag (auto) |
|-------------------|------------------|
| `[KAFKA]` | `kafka` |
| `[KAFKA, KINESIS]` | `kafka-kinesis` |
| All eight protocols | `all` |

Catalog: `deploy/helm/eventore/files/stream-providers.yaml` · CI matrix: `deploy/ci-backend-images.json`.

## Three pods (three workloads)

| # | Workload | Image | Role |
|---|----------|-------|------|
| 1 | **Backend** | `ghcr.io/vijayptiwari/eventore-backend:<tag>` | Spring API — tag matches `streamProviders` |
| 2 | **Frontend** | `ghcr.io/vijayptiwari/eventore-frontend:latest` | React UI (nginx) |
| 3 | **MCP** (optional) | `ghcr.io/vijayptiwari/eventore-mcp:latest` | AI agent MCP over HTTP |

## Helm

| Chart | Pods | Install |
|-------|------|---------|
| [`helm/eventore`](helm/eventore) | backend + frontend | Required |
| [`helm/eventore-mcp`](helm/eventore-mcp) | MCP | Optional |

### Kafka only

```bash
helm install eventore deploy/helm/eventore -f deploy/helm/eventore/values-stream-kafka.yaml
```

### Kafka + Kinesis

```bash
helm install eventore deploy/helm/eventore -f deploy/helm/eventore/values-kafka-kinesis.yaml
```

### Dev overlay (Kafka, MQTT, RabbitMQ)

```bash
helm install eventore deploy/helm/eventore -f deploy/helm/eventore/values-dev.yaml
```

### From OCI (published chart + image)

```bash
helm install eventore oci://ghcr.io/vijayptiwari/charts/eventore --version 0.1.0 \
  -f deploy/helm/eventore/values-stream-kafka.yaml
```

Add a new multi-provider combo: extend `deploy/ci-backend-images.json` and push to `main` so CI builds that tag.

## Published backend image tags (GHCR)

| Tag | Contents |
|-----|----------|
| `kafka`, `mqtt`, `jms`, `pulsar`, `rabbitmq`, `kinesis`, `gcp-pubsub`, `azure-servicebus` | Single stream provider each |
| `kafka-kinesis` | Kafka + Kinesis bundle |
| `all` | All eight providers |

Workflow: `.github/workflows/publish-artifacts.yml`. Frontend/MCP still use `latest` on `main`.

## Docker Compose (local)

```bash
docker compose -f docker/docker-compose.stack.yml up -d
```

Build a custom provider set:

```bash
docker build -f docker/Dockerfile.backend \
  --build-arg EVENTORE_STREAM_PROFILES=provider-kafka,provider-mqtt \
  -t eventore-backend:custom .
```

Local dev with every provider on classpath (not published to GHCR):

```bash
docker build -f docker/Dockerfile.backend \
  --build-arg EVENTORE_STREAM_PROFILES=providers-all \
  -t eventore-backend:dev .
```

## MCP (optional third pod)

```bash
helm install eventore-mcp deploy/helm/eventore-mcp \
  --set eventore.apiUrl=http://eventore-backend:8080/api/v1
```

Replace `eventore-backend` with `<release-name>-backend` when the release name is not `eventore`.
