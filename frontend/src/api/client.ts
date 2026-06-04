import { getRuntimeConfig } from '../config/runtime';
import type {
  AppConfig,
  ConnectionProfile,
  ControlPlaneView,
  ProtocolType,
  TopicRef,
  UnifiedMessage,
} from './types';
import type {
  ClusterInfo,
  ConsumerGroupDetail,
  ConsumerGroupSummary,
  GroupOffset,
  InspectCapabilities,
  MessageSearchRequest,
  TopicDetail,
} from './inspectTypes';
import type {
  CreateTopicRequest,
  KafkaAclEntry,
  PublishResult,
  ReplaceAclRequest,
} from './kafkaAdminTypes';

import type { StreamPlatformPreset } from './platformTypes';

export interface ConnectionProfileResponse {
  id?: string;
  name: string;
  protocol: ProtocolType;
  cloudProvider?: import('./types').CloudProvider;
  streamPlatform?: import('./types').StreamPlatform;
  brokerUrl: string;
  properties?: Record<string, string>;
  hasCredentials?: boolean;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const { apiBaseUrl } = getRuntimeConfig();
  const res = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  });
  if (!res.ok) {
    const text = await res.text();
    let msg = text;
    try {
      const j = JSON.parse(text) as { error?: string };
      if (j.error) msg = j.error;
    } catch {
      // use raw
    }
    throw new Error(msg || res.statusText);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

export const api = {
  getConfig: () => request<AppConfig>('/config'),
  getControlPlane: () => request<ControlPlaneView>('/control/plane'),
  listPlatforms: () => request<StreamPlatformPreset[]>('/platforms'),
  listConnections: () => request<ConnectionProfileResponse[]>('/connections'),
  createConnection: (body: ConnectionProfile) =>
    request<ConnectionProfileResponse>('/connections', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateConnection: (id: string, body: ConnectionProfile) =>
    request<ConnectionProfileResponse>(`/connections/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteConnection: (id: string) =>
    request<void>(`/connections/${id}`, { method: 'DELETE' }),
  validateConnection: (id: string) =>
    request<{ status: string }>(`/connections/${id}/validate`, { method: 'POST' }),
  listDestinations: (connectionId: string) =>
    request<TopicRef[]>(`/connections/${connectionId}/destinations`),
  publish: (
    connectionId: string,
    body: {
      destination: string;
      payload: string;
      contentType?: string;
      headers?: Record<string, string>;
    },
  ) =>
    request<{ status: string }>(`/connections/${connectionId}/publish`, {
      method: 'POST',
      body: JSON.stringify({
        destination: body.destination,
        payload: body.payload,
        contentType: body.contentType,
        headers: body.headers,
      }),
    }),

  inspectCapabilities: (connectionId: string) =>
    request<InspectCapabilities>(`/connections/${connectionId}/inspect/capabilities`),
  inspectCluster: (connectionId: string) =>
    request<ClusterInfo>(`/connections/${connectionId}/inspect/cluster`),
  inspectBrokers: (connectionId: string) =>
    request<{ cluster: ClusterInfo; brokerInfo: Record<string, unknown> }>(
      `/connections/${connectionId}/inspect/brokers`,
    ),
  inspectConsumerGroups: (connectionId: string) =>
    request<ConsumerGroupSummary[]>(`/connections/${connectionId}/inspect/consumer-groups`),
  inspectConsumerGroup: (connectionId: string, groupId: string) =>
    request<ConsumerGroupDetail>(
      `/connections/${connectionId}/inspect/consumer-groups/${encodeURIComponent(groupId)}`,
    ),
  inspectTopics: (connectionId: string, filter?: string) =>
    request<TopicDetail[]>(
      `/connections/${connectionId}/inspect/topics${filter ? `?filter=${encodeURIComponent(filter)}` : ''}`,
    ),
  inspectTopic: (connectionId: string, topic: string) =>
    request<TopicDetail>(
      `/connections/${connectionId}/inspect/topics/${encodeURIComponent(topic)}`,
    ),
  inspectLag: (connectionId: string, groupId: string, topic?: string) =>
    request<GroupOffset[]>(
      `/connections/${connectionId}/inspect/lag?groupId=${encodeURIComponent(groupId)}${topic ? `&topic=${encodeURIComponent(topic)}` : ''}`,
    ),
  inspectSearch: (connectionId: string, body: MessageSearchRequest) =>
    request<UnifiedMessage[]>(`/connections/${connectionId}/inspect/search`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  kafkaPublish: (
    connectionId: string,
    body: {
      destination: string;
      payload: string;
      contentType?: string;
      headers?: Record<string, string>;
      flush?: boolean;
    },
  ) => {
    const q = body.flush ? '?flush=true' : '';
    return request<PublishResult>(`/connections/${connectionId}/kafka/publish${q}`, {
      method: 'POST',
      body: JSON.stringify({
        destination: body.destination,
        payload: body.payload,
        contentType: body.contentType,
        headers: body.headers,
      }),
    });
  },
  kafkaCreateTopic: (connectionId: string, body: CreateTopicRequest) =>
    request<{ status: string; topic: string }>(`/connections/${connectionId}/kafka/topics`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  kafkaDeleteTopic: (connectionId: string, topic: string) =>
    request<{ status: string; topic: string }>(
      `/connections/${connectionId}/kafka/topics/${encodeURIComponent(topic)}`,
      { method: 'DELETE' },
    ),
  kafkaFlushTopic: (connectionId: string, topic: string, body: { mode?: string; partition?: number }) =>
    request<Record<string, unknown>>(
      `/connections/${connectionId}/kafka/topics/${encodeURIComponent(topic)}/flush`,
      { method: 'POST', body: JSON.stringify(body) },
    ),
  kafkaAlterTopicConfigs: (
    connectionId: string,
    topic: string,
    configs: Record<string, string>,
  ) =>
    request<{ status: string; topic: string }>(
      `/connections/${connectionId}/kafka/topics/${encodeURIComponent(topic)}/configs`,
      { method: 'PUT', body: JSON.stringify({ configs }) },
    ),
  kafkaListAcls: (
    connectionId: string,
    filter?: { resourceType?: string; resourceName?: string; principal?: string },
  ) => {
    const params = new URLSearchParams();
    if (filter?.resourceType) params.set('resourceType', filter.resourceType);
    if (filter?.resourceName) params.set('resourceName', filter.resourceName);
    if (filter?.principal) params.set('principal', filter.principal);
    const q = params.toString() ? `?${params}` : '';
    return request<KafkaAclEntry[]>(`/connections/${connectionId}/kafka/acls${q}`);
  },
  kafkaCreateAcl: (connectionId: string, entry: KafkaAclEntry) =>
    request<{ status: string }>(`/connections/${connectionId}/kafka/acls`, {
      method: 'POST',
      body: JSON.stringify(entry),
    }),
  kafkaDeleteAcl: (connectionId: string, entry: KafkaAclEntry) =>
    request<{ status: string }>(`/connections/${connectionId}/kafka/acls`, {
      method: 'DELETE',
      body: JSON.stringify(entry),
    }),
  kafkaReplaceAcl: (connectionId: string, body: ReplaceAclRequest) =>
    request<{ status: string }>(`/connections/${connectionId}/kafka/acls`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
};

export function canAction(allowed: string[] | undefined, action: string): boolean {
  return allowed?.includes(action) ?? false;
}

export const PROTOCOL_DEFAULTS: Record<ProtocolType, { brokerUrl: string; hint: string }> = {
  KAFKA: { brokerUrl: 'localhost:9092', hint: 'bootstrap.servers' },
  MQTT: { brokerUrl: 'localhost:1883', hint: 'tcp://host:1883' },
  JMS: { brokerUrl: 'localhost:61616', hint: 'Artemis tcp://host:61616' },
  PULSAR: { brokerUrl: 'pulsar://localhost:6650', hint: 'service URL' },
  RABBITMQ: { brokerUrl: 'localhost:5672', hint: 'host:port, managementPort=15672' },
  KINESIS: { brokerUrl: 'us-east-1', hint: 'AWS region (e.g. us-east-1)' },
  GCP_PUBSUB: { brokerUrl: 'my-gcp-project', hint: 'GCP project ID' },
  AZURE_SERVICE_BUS: {
    brokerUrl: 'my-namespace.servicebus.windows.net',
    hint: 'Namespace host; use connectionString credential',
  },
};
