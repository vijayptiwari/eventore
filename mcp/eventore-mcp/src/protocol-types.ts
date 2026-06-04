import { z } from 'zod';

/** Protocols that may appear on a deployed Eventore backend (Maven profile + registration). */
export const ALL_PROTOCOLS = [
  'KAFKA',
  'MQTT',
  'JMS',
  'PULSAR',
  'RABBITMQ',
  'KINESIS',
  'GCP_PUBSUB',
  'AZURE_SERVICE_BUS',
] as const;

export type ProtocolType = (typeof ALL_PROTOCOLS)[number];

export const ProtocolSchema = z.enum(ALL_PROTOCOLS);
