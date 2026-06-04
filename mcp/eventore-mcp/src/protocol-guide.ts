import type { ProtocolType } from './protocol-types.js';
import { ALL_PROTOCOLS } from './protocol-types.js';

export interface ProtocolGuide {
  protocol: ProtocolType;
  brokerUrlExample: string;
  requiredFields: string[];
  optionalProperties: Record<string, string>;
  publishHints: string;
  subscribeHints: string;
  notes?: string;
}

const GUIDES: Partial<Record<ProtocolType, ProtocolGuide>> = {
  KAFKA: {
    protocol: 'KAFKA',
    brokerUrlExample: 'localhost:9092',
    requiredFields: ['name', 'brokerUrl'],
    optionalProperties: { securityProtocol: 'PLAINTEXT', saslMechanism: 'PLAIN' },
    publishHints: 'Use destination=topic name. Optional header "key" for partition key.',
    subscribeHints: 'Optional consumerGroup; defaults to ephemeral eventore group.',
  },
  MQTT: {
    protocol: 'MQTT',
    brokerUrlExample: 'tcp://localhost:1883',
    requiredFields: ['name', 'brokerUrl'],
    optionalProperties: { topicFilter: '#' },
    publishHints: 'destination=MQTT topic. Header qos=0|1|2.',
    subscribeHints: 'Subscribe to exact topic or wildcard per broker rules.',
  },
  JMS: {
    protocol: 'JMS',
    brokerUrlExample: 'tcp://localhost:61616',
    requiredFields: ['name', 'brokerUrl'],
    optionalProperties: { queue: 'eventore.queue', topic: 'eventore.topic' },
    publishHints: 'Header destinationType=queue|topic.',
    subscribeHints: 'Option destinationType=queue|topic in subscribe options.',
  },
  PULSAR: {
    protocol: 'PULSAR',
    brokerUrlExample: 'pulsar://localhost:6650',
    requiredFields: ['name', 'brokerUrl'],
    optionalProperties: { adminUrl: 'http://localhost:8080' },
    publishHints: 'Use full topic persistent://tenant/ns/name or short name.',
    subscribeHints: 'consumerGroup maps to Pulsar subscription name.',
  },
  RABBITMQ: {
    protocol: 'RABBITMQ',
    brokerUrlExample: 'localhost:5672',
    requiredFields: ['name', 'brokerUrl'],
    optionalProperties: { vhost: '/', queue: 'eventore.queue', exchange: '' },
    publishHints: 'Headers exchange, routingKey (defaults to destination).',
    subscribeHints: 'destination=queue name to bind consumer.',
  },
  KINESIS: {
    protocol: 'KINESIS',
    brokerUrlExample: 'kinesis://us-east-1',
    requiredFields: ['name', 'brokerUrl'],
    optionalProperties: { streamName: 'my-stream', region: 'us-east-1' },
    publishHints: 'destination=stream name. Use AWS credentials on the connection.',
    subscribeHints: 'Shard iterator via backend Kinesis provider; check control plane registration.',
    notes: 'Requires Kinesis provider JAR and control-plane registration.',
  },
  GCP_PUBSUB: {
    protocol: 'GCP_PUBSUB',
    brokerUrlExample: 'pubsub://projects/my-project',
    requiredFields: ['name', 'brokerUrl'],
    optionalProperties: { topic: 'events', subscription: 'events-sub' },
    publishHints: 'destination=topic id. Service account JSON in credentials if needed.',
    subscribeHints: 'Pull subscription configured on connection properties.',
    notes: 'Requires GCP Pub/Sub provider module.',
  },
  AZURE_SERVICE_BUS: {
    protocol: 'AZURE_SERVICE_BUS',
    brokerUrlExample: 'sb://my-namespace.servicebus.windows.net',
    requiredFields: ['name', 'brokerUrl'],
    optionalProperties: { queue: 'my-queue', topic: 'my-topic' },
    publishHints: 'destination=queue or topic path. Connection string in credentials.',
    subscribeHints: 'Uses Azure Service Bus SDK via Eventore data plane.',
    notes: 'Requires Azure Service Bus provider module.',
  },
};

export function getProtocolGuide(protocol: ProtocolType): ProtocolGuide {
  const guide = GUIDES[protocol];
  if (guide) return guide;
  return {
    protocol,
    brokerUrlExample: '(see Eventore UI presets)',
    requiredFields: ['name', 'brokerUrl'],
    optionalProperties: {},
    publishHints: 'Use data-plane publish after provider is registered.',
    subscribeHints: 'Use eventore_consume_messages with destination from list_destinations.',
    notes: 'Provider must be registered in control plane before connections succeed.',
  };
}

export function suggestProtocol(hint: string): ProtocolType[] {
  const h = hint.toLowerCase();
  const matches: ProtocolType[] = [];
  if (h.includes('kafka') || h.includes('redpanda') || h.includes('9092')) matches.push('KAFKA');
  if (h.includes('mqtt') || h.includes('mosquitto') || h.includes('1883')) matches.push('MQTT');
  if (h.includes('jms') || h.includes('artemis') || h.includes('activemq')) matches.push('JMS');
  if (h.includes('pulsar') || h.includes('6650')) matches.push('PULSAR');
  if (h.includes('rabbit') || h.includes('amqp') || h.includes('5672')) matches.push('RABBITMQ');
  if (h.includes('kinesis') || h.includes('aws stream')) matches.push('KINESIS');
  if (h.includes('pubsub') || h.includes('gcp') || h.includes('google cloud')) matches.push('GCP_PUBSUB');
  if (h.includes('azure') || h.includes('service bus') || h.includes('event hubs'))
    matches.push('AZURE_SERVICE_BUS');
  return matches.length > 0 ? matches : [...ALL_PROTOCOLS];
}

export function allGuides(): ProtocolGuide[] {
  return ALL_PROTOCOLS.map((p) => getProtocolGuide(p));
}
