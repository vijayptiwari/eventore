export interface KafkaAclEntry {
  resourceType: string;
  resourceName: string;
  patternType?: string;
  principal: string;
  host?: string;
  operation: string;
  permissionType?: string;
}

export interface CreateTopicRequest {
  name: string;
  partitions?: number;
  replicationFactor?: number;
  configs?: Record<string, string>;
}

export interface PublishResult {
  topic: string;
  partition?: number;
  offset?: number;
  status: string;
}

export interface ReplaceAclRequest {
  oldBinding: KafkaAclEntry;
  newBinding: KafkaAclEntry;
}
