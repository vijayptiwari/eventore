export interface ClusterInfo {
  clusterId?: string;
  brokers?: { id: number; host: string; port: number; rack?: string }[];
  attributes?: Record<string, string>;
}

export interface ConsumerGroupSummary {
  groupId: string;
  state?: string;
  protocolType?: string;
  memberCount?: number;
  attributes?: Record<string, string>;
}

export interface GroupOffset {
  topic: string;
  partition?: number;
  offset?: number;
  logEndOffset?: number;
  lag: number;
  oldestUnackedMessageAge?: number | null;
}

export interface ConsumerGroupDetail {
  groupId: string;
  state?: string;
  partitionAssignor?: string;
  members?: { memberId: string; clientId: string; host: string; assignments: string[] }[];
  offsets?: GroupOffset[];
}

export interface TopicDetail {
  name: string;
  partitionCount?: number;
  replicationFactor?: number;
  partitions?: { partition: number; leader: number; replicas: number[]; isr: number[] }[];
  config?: Record<string, string>;
}

export interface MessageSearchRequest {
  topic: string;
  partition?: string;
  keyContains?: string;
  payloadContains?: string;
  fromOffset?: number;
  toOffset?: number;
  maxMessages?: number;
  startAt?: string;
  fromTimestamp?: string;
  mask?: boolean;
}

export interface InspectCapabilities {
  features: string[];
}

export interface MaskingConfigResponse {
  enabled: boolean;
  replacement: string;
  sensitiveFieldPatterns: string[];
  sensitiveHeaderPatterns: string[];
  maskValuesByPattern: boolean;
}

export interface MaskingPreviewRequest {
  payload: string;
  contentType?: string;
}

export interface MaskingPreviewResponse {
  original: string;
  masked: string;
  wasMasked: boolean;
}
