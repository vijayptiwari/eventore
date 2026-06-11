import type { CloudProvider, ProtocolType, StreamPlatform } from './types';

export type { CloudProvider, StreamPlatform };

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
