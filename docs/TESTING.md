# Testing

## Unit tests

Default Maven build excludes `@Tag("integration")` tests:

```bash
cd backend && mvn test
cd frontend && npm test
cd mcp/eventore-mcp && npm test
```

Frontend production typecheck runs in CI via `npm run build` (`tsc -b`); test files are excluded from the app `tsconfig.json` project.

## Integration tests (Testcontainers)

Requires Docker. Run all broker integration tests (matches the CI job):

```bash
cd backend
mvn test -DexcludedTestGroups= -pl eventore-provider-kafka,eventore-provider-rabbitmq,eventore-provider-mqtt,eventore-provider-pulsar,eventore-provider-jms -am
```

| Module | Container image | Notes |
|--------|-----------------|-------|
| eventore-provider-kafka | confluentinc/cp-kafka:7.4.0 | validate, publish/subscribe, binary base64 |
| eventore-provider-rabbitmq | rabbitmq:3.13-management | validate, queue round-trip |
| eventore-provider-mqtt | eclipse-mosquitto:2.0 | `mosquitto.conf` mounted for anonymous access; subscribe before publish |
| eventore-provider-pulsar | apachepulsar/pulsar:3.3.2 standalone | Admin HTTP wait + `public/default` bootstrap; profile sets `adminUrl` |
| eventore-provider-jms | apache/activemq-artemis:2.37.0 | validate, publish/subscribe queue |

## CI skip rationale (cloud protocols)

These protocols are **not** covered by Testcontainers in CI (see `docs/CLOUD-CI-SPIKE.md`):

| Protocol | Reason |
|----------|--------|
| KINESIS | AWS API; LocalStack Kinesis partial parity |
| GCP_PUBSUB | Service account + project required |
| AZURE_SERVICE_BUS | Namespace connection string required |

## CI pipeline

Workflow: `.github/workflows/publish-artifacts.yml` (on push to `main`, tags `v*`, and PRs to `main`).

| Job | Command / scope | Gates publish |
|-----|-----------------|---------------|
| `build-backend` | `mvn -B package` (all modules, unit tests) | backend images, helm |
| `backend-integration-tests` | Five Testcontainers broker modules (above) | backend images, helm |
| `build-frontend` | `npm ci`, Vitest, `npm run build` | frontend image, helm |
| `build-frontend-e2e` | Playwright mocked smoke (Chromium) | frontend image, helm |
| `build-mcp` | `npm ci && npm test` | MCP image |
| `helm-lint` | `helm lint` + template render | helm, console images |
| `publish-backend-images` | Matrix from `deploy/ci-backend-images.json` | — |
| `publish-console-images` | Frontend + MCP Docker push | — |
| `publish-helm` | OCI chart push | — |

Backend and integration jobs must pass before backend images and the Helm chart are published.

## Playwright E2E (mocked)

```bash
cd frontend
npm ci
npx playwright install chromium --with-deps
npm run test:e2e
```

Routes are stubbed in `frontend/e2e/fixtures.ts` — no backend required. CI runs wizard + diagnostics card smoke tests.

## Playwright Live E2E (unmocked, REQ-61)

```bash
# 1. Start Redpanda Kafka broker (or local Kafka on port 9092)
docker compose -f docker/docker-compose.brokers.yml up -d redpanda

# 2. Start Eventore backend
cd backend && mvn package -DskipTests -pl eventore-server -am
java -jar eventore-server/target/eventore-server-*.jar

# 3. In another terminal, run unmocked live E2E
cd frontend
npm run test:e2e:live
```

`frontend/e2e/live.spec.ts` verifies unmocked configuration cascades, real connection creation, backend-delegated broker validation, and unmocked deletion against a running backend. When the backend is not reachable, live tests are automatically skipped without failing test suites.

## Observability artifacts

Import `deploy/grafana/eventore-subscription-health.json` after Prometheus scrapes `/actuator/prometheus`.

Helm wires `eventore.diagnostics.errorSubscriptionThreshold` into `SPRING_APPLICATION_JSON`.

## Security regression tests

`eventore-server` includes:

- `ApiTokenSecurityIntegrationTest` — REST 401, diagnostics auth, SSE 401, WebSocket handshake 401
- `StreamSseControllerTest` — SSE ownership 403
- `DeploymentModePolicyIntegrationTest` — READONLY blocks connection create
- `InspectApiDelegateImplTest` — inspect policy delegation
