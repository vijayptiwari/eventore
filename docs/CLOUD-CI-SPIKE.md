# Cloud Broker CI Spike (REQ-58)

**Date:** 2026-06-11  
**Status:** Decision record — mock-only for OSS v1

## Options evaluated

| Protocol | Option | CI cost | Parity | Verdict |
|----------|--------|---------|--------|---------|
| KINESIS | LocalStack Kinesis | Medium | Partial (shard APIs differ) | Defer |
| GCP_PUBSUB | Pub/Sub emulator | Medium | Good for publish/pull | Defer |
| AZURE_SERVICE_BUS | Azurite / test namespace | High (namespace $) | Limited for Service Bus | Defer |

## Decision

**Keep unit + inspector tests for cloud protocols.** Integration tests remain on the five on-prem brokers (Kafka, RabbitMQ, MQTT, Pulsar, JMS) where Testcontainers images are mature and free in CI.

## Rationale

- Cloud connectors already have unit tests and inspector tests with mocked SDK clients.
- Emulator maintenance adds flaky CI and credential management without proportional OSS user value.
- Enterprise customers validate cloud paths in their own namespaces before production.

## Revisit when

- A paying customer requires cloud CI gates, or
- LocalStack Kinesis parity reaches ≥90% for shard iterator flows, or
- Monthly CI budget approved for GCP/Azure test namespaces.

## Follow-on REQs

- REQ-61: Optional live-stack Playwright with Kafka only (on-prem path)
- REQ-62: OpenAPI drift CI after REQ-51 lands
