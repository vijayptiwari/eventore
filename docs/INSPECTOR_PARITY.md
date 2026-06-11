# Inspector parity matrix

Capability tokens drive UI tab gating (`hasInspectFeature`). This matrix documents the honest supported surface per protocol as of Wave 4.

| Protocol | cluster / broker | destinations | groups / subscriptions | backlog / lag | message search | admin extras |
|----------|------------------|--------------|------------------------|---------------|----------------|--------------|
| **KAFKA** | cluster | topics, topic-detail | consumer groups, group-detail | partition lag | offset search | ACL admin |
| **RABBITMQ** | broker-info | queues, queue-detail | — (queue-centric UI) | queue depth | management get | — |
| **MQTT** | broker-info | topics, topic-filter | — | — | — | — |
| **PULSAR** | cluster | topics | subscriptions | subscription backlog | — | — |
| **JMS** | broker-info | queues, topics | — | — | — | — |
| **KINESIS** | cluster, streams | stream-detail | — | — | — | list shards (admin) |
| **GCP_PUBSUB** | cluster | topics | subscriptions | subscription backlog | not supported (501) | — |
| **AZURE_SERVICE_BUS** | cluster | queues, topics, queue-detail | topic subscriptions | queue/subscription counts | peek (non-destructive) | — |

## Notes

- **GCP Pub/Sub:** `searchMessages` remains unsupported; use live view or GCP Console. Backlog uses Admin API when `numUndeliveredMessages` is present on the subscription resource.
- **Azure Service Bus:** `searchMessages` uses peek — messages are not removed. Topic peek requires `partition` (subscription name) on the search request.
- **RabbitMQ:** The Groups tab is hidden; queue labels are used in the inspector chrome (FEAT-3.4).
- **Kinesis:** Shard listing requires `admin=true` on the provider descriptor.

## Deferred / emulator gaps

KINESIS, GCP_PUBSUB, and AZURE_SERVICE_BUS do not have Testcontainers integration tests in CI (credential/emulator cost). JMS is covered via Artemis in CI. See `docs/TESTING.md` and `docs/CLOUD-CI-SPIKE.md`.
