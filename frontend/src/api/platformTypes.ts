import type { ProtocolType } from './types';

export type CloudProvider = 'ON_PREM' | 'AWS' | 'AZURE' | 'GCP' | 'OCP';

export type StreamPlatform =
  | 'GENERIC'
  | 'AWS_MSK'
  | 'AWS_KINESIS'
  | 'AWS_MQ'
  | 'AWS_IOT_CORE'
  | 'AWS_SQS'
  | 'AZURE_EVENT_HUBS'
  | 'AZURE_SERVICE_BUS'
  | 'AZURE_IOT_HUB'
  | 'GCP_PUBSUB'
  | 'GCP_MANAGED_KAFKA'
  | 'OCP_AMQ_STREAMS'
  | 'OCP_STRIMZI_KAFKA';

export interface StreamPlatformPreset {
  platform: StreamPlatform;
  label: string;
  description: string;
  cloudProvider: CloudProvider;
  protocol: ProtocolType;
  brokerUrlHint: string;
  defaultProperties: Record<string, string>;
  credentialFields: string[];
  features: string[];
}
