import type { ProtocolType } from './eventore-client.js';

export interface ProtocolGuide {
  protocol: ProtocolType;
  brokerUrlExample: string;
  requiredFields: string[];
  optionalProperties: Record<string, string>;
  publishHints: string;
  subscribeHints: string;
}

const GUIDES: Record<ProtocolType, ProtocolGuide> = {
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
};

export function getProtocolGuide(protocol: ProtocolType): ProtocolGuide {
  return GUIDES[protocol];
}

export function suggestProtocol(hint: string): ProtocolType[] {
  const h = hint.toLowerCase();
  const matches: ProtocolType[] = [];
  if (h.includes('kafka') || h.includes('redpanda') || h.includes('9092')) matches.push('KAFKA');
  if (h.includes('mqtt') || h.includes('mosquitto') || h.includes('1883')) matches.push('MQTT');
  if (h.includes('jms') || h.includes('artemis') || h.includes('activemq')) matches.push('JMS');
  if (h.includes('pulsar') || h.includes('6650')) matches.push('PULSAR');
  if (h.includes('rabbit') || h.includes('amqp') || h.includes('5672')) matches.push('RABBITMQ');
  return matches.length > 0 ? matches : ['KAFKA', 'MQTT', 'JMS', 'PULSAR', 'RABBITMQ'];
}

export function allGuides(): ProtocolGuide[] {
  return Object.values(GUIDES);
}
