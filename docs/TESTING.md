# Testing

## Unit tests

Default Maven build excludes `@Tag("integration")` tests:

```bash
cd backend && mvn test
cd frontend && npm test
```

## Integration tests (Testcontainers)

Requires Docker. Run all broker integration tests:

```bash
cd backend
mvn test -DexcludedTestGroups= -pl eventore-provider-kafka,eventore-provider-rabbitmq,eventore-provider-mqtt,eventore-provider-pulsar -am
```

| Module | Container image | Coverage |
|--------|-----------------|----------|
| eventore-provider-kafka | confluentinc/cp-kafka | validate, publish/subscribe |
| eventore-provider-rabbitmq | rabbitmq:3.13-management | validate, publish/subscribe |
| eventore-provider-mqtt | eclipse-mosquitto:2.0 | validate, publish/subscribe |
| eventore-provider-pulsar | apachepulsar/pulsar:3.3.2 standalone | validate, publish/subscribe |

## CI skip rationale

These protocols are **not** covered by Testcontainers in CI:

| Protocol | Reason |
|----------|--------|
| KINESIS | AWS API; LocalStack Kinesis partial parity |
| GCP_PUBSUB | Service account + project required |
| AZURE_SERVICE_BUS | Namespace connection string required |
| JMS | Broker-specific images (Artemis/ActiveMQ) not yet matrixed |

CI runs integration tests on every PR to `main` via `.github/workflows/publish-artifacts.yml`.

## Observability artifacts

Import `deploy/grafana/eventore-subscription-health.json` after Prometheus scrapes `/actuator/prometheus`.

Helm wires `eventore.diagnostics.errorSubscriptionThreshold` into `SPRING_APPLICATION_JSON`.
