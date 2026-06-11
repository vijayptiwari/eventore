# Testing

## Unit tests

Default Maven build excludes `@Tag("integration")` tests:

```bash
cd backend && mvn test
cd frontend && npm test
cd mcp/eventore-mcp && npm test
```

## Integration tests (Testcontainers)

Requires Docker. Run all broker integration tests:

```bash
cd backend
mvn test -DexcludedTestGroups= -pl eventore-provider-kafka,eventore-provider-rabbitmq,eventore-provider-mqtt,eventore-provider-pulsar,eventore-provider-jms -am
```

| Module | Container image | Coverage |
|--------|-----------------|----------|
| eventore-provider-kafka | confluentinc/cp-kafka | validate, publish/subscribe |
| eventore-provider-rabbitmq | rabbitmq:3.13-management | validate, publish/subscribe |
| eventore-provider-mqtt | eclipse-mosquitto:2.0 | validate, publish/subscribe |
| eventore-provider-pulsar | apachepulsar/pulsar:3.3.2 standalone | validate, publish/subscribe |
| eventore-provider-jms | apache/activemq-artemis:2.37.0 | validate, publish/subscribe queue |

## CI skip rationale (cloud protocols)

These protocols are **not** covered by Testcontainers in CI (see `docs/CLOUD-CI-SPIKE.md`):

| Protocol | Reason |
|----------|--------|
| KINESIS | AWS API; LocalStack Kinesis partial parity |
| GCP_PUBSUB | Service account + project required |
| AZURE_SERVICE_BUS | Namespace connection string required |

CI runs on every PR to `main` via `.github/workflows/publish-artifacts.yml`:

- `build-backend` — Maven unit tests (all modules)
- `backend-integration-tests` — five Testcontainers broker modules
- `build-mcp` — `npm ci && npm test` in `mcp/eventore-mcp`
- `build-frontend` — Vitest
- `build-frontend-e2e` — Playwright mocked smoke (Chromium)

## Playwright E2E (mocked)

```bash
cd frontend
npm ci
npx playwright install chromium --with-deps
npm run test:e2e
```

Routes are stubbed in `frontend/e2e/fixtures.ts` — no backend required.

## Observability artifacts

Import `deploy/grafana/eventore-subscription-health.json` after Prometheus scrapes `/actuator/prometheus`.

Helm wires `eventore.diagnostics.errorSubscriptionThreshold` into `SPRING_APPLICATION_JSON`.

## Security regression tests

`eventore-server` includes:

- `ApiTokenSecurityIntegrationTest` — REST 401, diagnostics auth, SSE 401, WebSocket handshake 401
- `StreamSseControllerTest` — SSE ownership 403
- `DeploymentModePolicyIntegrationTest` — READONLY blocks connection create
