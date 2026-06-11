import { PROTOCOL_DEFAULTS } from '../api/client';
import type { StreamPlatformPreset } from '../api/platformTypes';
import type { ConnectionProfile, ProtocolType } from '../api/types';
import { portalMeta } from '../config/portalMeta';

export interface ProtocolFieldDescriptor {
  kind: 'property' | 'credential';
  key: string;
  label: string;
  defaultValue?: string;
  password?: boolean;
  syncBrokerUrl?: boolean;
}

export const PROTOCOL_EXTRA_FIELDS: Partial<Record<ProtocolType, ProtocolFieldDescriptor[]>> = {
  MQTT: [{ kind: 'property', key: 'topicFilter', label: 'Topic filter', defaultValue: '#' }],
  RABBITMQ: [
    { kind: 'property', key: 'vhost', label: 'Virtual host', defaultValue: '/' },
    { kind: 'property', key: 'queue', label: 'Default queue', defaultValue: 'eventore.queue' },
  ],
  GCP_PUBSUB: [
    {
      kind: 'property',
      key: 'subscription',
      label: 'Subscription name (for consume)',
      defaultValue: 'eventore-sub',
    },
  ],
  AZURE_SERVICE_BUS: [
    {
      kind: 'property',
      key: 'entityPath',
      label: 'Entity path (queue or topic)',
      defaultValue: 'eventore',
    },
    { kind: 'credential', key: 'connectionString', label: 'Connection string', password: true },
  ],
  KINESIS: [
    { kind: 'property', key: 'region', label: 'AWS region', syncBrokerUrl: true },
    { kind: 'credential', key: 'accessKeyId', label: 'Access key ID' },
    { kind: 'credential', key: 'secretAccessKey', label: 'Secret access key', password: true },
  ],
  JMS: [{ kind: 'property', key: 'queue', label: 'Default queue', defaultValue: 'eventore.queue' }],
};

const PROTOCOL_GUIDE_SLUG: Record<ProtocolType, string> = {
  KAFKA: 'kafka',
  MQTT: 'mqtt',
  JMS: 'jms',
  PULSAR: 'pulsar',
  RABBITMQ: 'rabbitmq',
  KINESIS: 'kinesis',
  GCP_PUBSUB: 'gcp-pubsub',
  AZURE_SERVICE_BUS: 'azure-servicebus',
};

export function protocolGuideUrl(protocol: ProtocolType): string {
  const base = portalMeta.guideUrl.replace(/\/index\.html$/, '');
  return `${base}/${PROTOCOL_GUIDE_SLUG[protocol]}.html`;
}

export const SECRETS_DOC_URL = `${portalMeta.docsUrl}guide/deployment.html`;

export function defaultProtocol(protocols: ProtocolType[]): ProtocolType {
  return protocols[0] ?? 'KAFKA';
}

export function emptyForm(protocol: ProtocolType): ConnectionProfile {
  return {
    name: '',
    protocol,
    cloudProvider: 'ON_PREM',
    streamPlatform: 'GENERIC',
    brokerUrl: PROTOCOL_DEFAULTS[protocol].brokerUrl,
    properties: {},
    credentials: {},
  };
}

export function presetKey(preset: StreamPlatformPreset): string {
  return `${preset.platform}-${preset.protocol}-${preset.label}`;
}

export function applyPresetToForm(
  form: ConnectionProfile,
  preset: StreamPlatformPreset,
): ConnectionProfile {
  return {
    ...form,
    protocol: preset.protocol,
    cloudProvider: preset.cloudProvider,
    streamPlatform: preset.platform,
    brokerUrl: preset.brokerUrlHint,
    properties: { ...preset.defaultProperties },
  };
}
