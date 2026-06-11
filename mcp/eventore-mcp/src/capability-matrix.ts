/** Mirrors docs/INSPECTOR_PARITY.md for MCP resource eventore://capability-matrix */
export const INSPECTOR_CAPABILITY_MATRIX = {
  version: 'wave2',
  protocols: {
    KAFKA: ['cluster', 'topics', 'consumer-groups', 'lag', 'message-search', 'kafka-admin'],
    RABBITMQ: ['broker-info', 'queues', 'queue-detail', 'message-search', 'lag'],
    MQTT: ['broker-info', 'topics', 'topic-filter'],
    PULSAR: ['cluster', 'topics', 'subscriptions', 'backlog'],
    JMS: ['broker-info', 'queues', 'topics'],
    KINESIS: ['cluster', 'streams', 'stream-detail', 'admin-shards'],
    GCP_PUBSUB: ['cluster', 'topics', 'subscriptions', 'backlog'],
    AZURE_SERVICE_BUS: ['cluster', 'queues', 'topics', 'subscriptions', 'message-search', 'backlog'],
  },
  notes: [
    'Use eventore_inspect_capabilities per connection for live tokens.',
    'GCP message-search is not supported (501).',
    'Azure message-search uses non-destructive peek.',
  ],
};
