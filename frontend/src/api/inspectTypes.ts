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
}

export interface GroupOffset {
  topic: string;
  partition?: number;
  offset?: number;
  logEndOffset?: number;
  lag: number;
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
}

export interface InspectCapabilities {
  features: string[];
}
