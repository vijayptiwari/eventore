import { getRuntimeConfig } from '../config/runtime';
import type {
  AppConfig,
  ConnectionProfile,
  ConnectionStoreInfo,
  ConnectionStoreType,
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

export interface SubscriptionDiagnostic {
  subscriptionId: string;
  connectionId: string;
  connectionName: string;
  protocol: string;
  destination: string;
  transport: string;
  messageCount: number;
  lastError: string | null;
  startedAt: string;
  status: 'ACTIVE' | 'ERROR' | 'SLOW_CONSUMER';
}

export interface ValidationRecord {
  timestamp: string;
  status: 'OK' | 'FAILED';
  message: string;
}

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

export interface SchemaMetadata {
  schemaId: number;
  subject?: string;
  version?: number;
  schemaType?: string;
  schemaContent: string;
}

export interface SchemaValidationResult {
  valid: boolean;
  errors: string[];
}

export interface SchemaTemplateResponse {
  schemaId: number;
  schemaType: string;
  template: string;
}

export interface DlqTopicSummary {
  dlqTopic: string;
  inferredTargetTopic: string;
  partitionCount: number;
  detectionReason: string;
}

export interface DlqMessageInfo {
  message: UnifiedMessage;
  originalTopic?: string;
  failureReason?: string;
  exceptionClass?: string;
  stackTraceSnippet?: string;
}

export interface DlqRedriveRequest {
  sourceTopic: string;
  targetTopic?: string;
  messageIds?: string[];
  editedPayload?: string;
  editedHeaders?: Record<string, string>;
  maxMessages?: number;
}

export interface DlqRedriveResult {
  sourceTopic: string;
  targetTopic: string;
  redrivenCount: number;
  failedCount: number;
  errors: string[];
  timestamp: string;
}

export type { ConnectionStoreInfo, ConnectionStoreType };

const REQUEST_TIMEOUT_MS = 30_000;

export class InspectNotSupportedError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = 'InspectNotSupportedError';
    this.status = status;
    this.code = code;
  }
}

export function isApiAuthError(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false;
  }
  const msg = error.message.toLowerCase();
  return msg.includes('api token') || msg.includes('unauthorized') || msg.includes('401');
}

export function authHeaders(): Record<string, string> {
  const { apiToken } = getRuntimeConfig();
  if (!apiToken) {
    return {};
  }
  return {
    Authorization: `Bearer ${apiToken}`,
  };
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const { apiBaseUrl } = getRuntimeConfig();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  let res: Response;
  try {
    res = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      signal: init?.signal ?? controller.signal,
      headers: {
        'Content-Type': 'application/json',
        ...authHeaders(),
        ...(init?.headers ?? {}),
      },
    });
  } catch (err) {
    if (!init?.signal && controller.signal.aborted) {
      throw new Error(`Request timed out after ${REQUEST_TIMEOUT_MS}ms: ${path}`);
    }
    throw err;
  } finally {
    clearTimeout(timer);
  }
  if (!res.ok) {
    let msg = '';
    let code: string | undefined;
    try {
      const text = await res.text();
      msg = text;
      const j = JSON.parse(text) as { error?: string; code?: string };
      if (j.error) msg = j.error;
      code = j.code;
    } catch {
      // non-JSON or unreadable body — keep raw text (or fall back to status text)
    }
    if (res.status === 501 || code === 'EVT-1501') {
      throw new InspectNotSupportedError(msg || res.statusText, res.status, code);
    }
    throw new Error(msg || res.statusText);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

function connectionPath(connectionId: string, suffix: string): string {
  return `/connections/${encodeURIComponent(connectionId)}${suffix}`;
}

export const api = {
  getConfig: () => request<AppConfig>('/config'),
  getControlPlane: () => request<ControlPlaneView>('/control/plane'),
  listPlatforms: () => request<StreamPlatformPreset[]>('/platforms'),
  listConnections: () => request<ConnectionProfileResponse[]>('/connections'),
  getConnectionStoreInfo: () => request<ConnectionStoreInfo>('/connections/store'),
  migrateConnectionStore: (targetType: ConnectionStoreType) =>
    request<ConnectionStoreInfo>(`/connections/store/migrate?targetType=${encodeURIComponent(targetType)}`, {
      method: 'POST',
    }),
  createConnection: (body: ConnectionProfile) =>
    request<ConnectionProfileResponse>('/connections', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateConnection: (id: string, body: ConnectionProfile) =>
    request<ConnectionProfileResponse>(connectionPath(id, ''), {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteConnection: (id: string) =>
    request<void>(connectionPath(id, ''), { method: 'DELETE' }),
  validateConnection: (id: string) =>
    request<{ status: string }>(connectionPath(id, '/validate'), { method: 'POST' }),
  listDestinations: (connectionId: string) =>
    request<TopicRef[]>(connectionPath(connectionId, '/destinations')),
  publish: (
    connectionId: string,
    body: {
      destination: string;
      payload: string;
      contentType?: string;
      headers?: Record<string, string>;
    },
  ) =>
    request<{ status: string }>(connectionPath(connectionId, '/publish'), {
      method: 'POST',
      body: JSON.stringify({
        destination: body.destination,
        payload: body.payload,
        contentType: body.contentType,
        headers: body.headers,
      }),
    }),

  inspectCapabilities: (connectionId: string) =>
    request<InspectCapabilities>(connectionPath(connectionId, '/inspect/capabilities')),
  inspectCluster: (connectionId: string) =>
    request<ClusterInfo>(connectionPath(connectionId, '/inspect/cluster')),
  inspectBrokers: (connectionId: string) =>
    request<{ cluster: ClusterInfo; brokerInfo: Record<string, unknown> }>(
      connectionPath(connectionId, '/inspect/brokers'),
    ),
  inspectConsumerGroups: (connectionId: string) =>
    request<ConsumerGroupSummary[]>(connectionPath(connectionId, '/inspect/consumer-groups')),
  inspectConsumerGroup: (connectionId: string, groupId: string) =>
    request<ConsumerGroupDetail>(
      `${connectionPath(connectionId, '/inspect/consumer-groups/')}${encodeURIComponent(groupId)}`,
    ),
  inspectTopics: (connectionId: string, filter?: string) =>
    request<TopicDetail[]>(
      `${connectionPath(connectionId, '/inspect/topics')}${filter ? `?filter=${encodeURIComponent(filter)}` : ''}`,
    ),
  inspectTopic: (connectionId: string, topic: string) =>
    request<TopicDetail>(
      `${connectionPath(connectionId, '/inspect/topics/')}${encodeURIComponent(topic)}`,
    ),
  inspectLag: (connectionId: string, groupId: string, topic?: string) =>
    request<GroupOffset[]>(
      `${connectionPath(connectionId, '/inspect/lag')}?groupId=${encodeURIComponent(groupId)}${topic ? `&topic=${encodeURIComponent(topic)}` : ''}`,
    ),
  inspectSearch: (connectionId: string, body: MessageSearchRequest) =>
    request<UnifiedMessage[]>(connectionPath(connectionId, '/inspect/search'), {
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
    return request<PublishResult>(`${connectionPath(connectionId, '/kafka/publish')}${q}`, {
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
    request<{ status: string; topic: string }>(connectionPath(connectionId, '/kafka/topics'), {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  kafkaDeleteTopic: (connectionId: string, topic: string) =>
    request<{ status: string; topic: string }>(
      `${connectionPath(connectionId, '/kafka/topics/')}${encodeURIComponent(topic)}`,
      { method: 'DELETE' },
    ),
  kafkaFlushTopic: (connectionId: string, topic: string, body: { mode?: string; partition?: number }) =>
    request<Record<string, unknown>>(
      `${connectionPath(connectionId, '/kafka/topics/')}${encodeURIComponent(topic)}/flush`,
      { method: 'POST', body: JSON.stringify(body) },
    ),
  kafkaAlterTopicConfigs: (
    connectionId: string,
    topic: string,
    configs: Record<string, string>,
  ) =>
    request<{ status: string; topic: string }>(
      `${connectionPath(connectionId, '/kafka/topics/')}${encodeURIComponent(topic)}/configs`,
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
    return request<KafkaAclEntry[]>(`${connectionPath(connectionId, '/kafka/acls')}${q}`);
  },
  kafkaCreateAcl: (connectionId: string, entry: KafkaAclEntry) =>
    request<{ status: string }>(connectionPath(connectionId, '/kafka/acls'), {
      method: 'POST',
      body: JSON.stringify(entry),
    }),
  kafkaDeleteAcl: (connectionId: string, entry: KafkaAclEntry) =>
    request<{ status: string }>(connectionPath(connectionId, '/kafka/acls'), {
      method: 'DELETE',
      body: JSON.stringify(entry),
    }),
  kafkaReplaceAcl: (connectionId: string, body: ReplaceAclRequest) =>
    request<{ status: string }>(connectionPath(connectionId, '/kafka/acls'), {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  diagnosticsSubscriptions: () =>
    request<SubscriptionDiagnostic[]>('/diagnostics/subscriptions'),

  diagnosticsValidations: (connectionId: string) =>
    request<ValidationRecord[]>(`/diagnostics/connections/${connectionId}/validations`),

  kinesisListShards: (connectionId: string, streamName: string) =>
    request<
      Array<{
        shardId: string;
        hashKeyRange?: string;
        sequenceNumberRange?: string;
      }>
    >(
      `${connectionPath(connectionId, '/kinesis/streams/')}${encodeURIComponent(streamName)}/shards`,
    ),

  getSchemas: () => request<SchemaMetadata[]>('/schemas'),
  getSchemaById: (id: number, registryUrl?: string) =>
    request<SchemaMetadata>(
      `/schemas/${id}${registryUrl ? `?registryUrl=${encodeURIComponent(registryUrl)}` : ''}`
    ),
  getSchemaTemplate: (id: number, registryUrl?: string) =>
    request<SchemaTemplateResponse>(
      `/schemas/${id}/template${registryUrl ? `?registryUrl=${encodeURIComponent(registryUrl)}` : ''}`
    ),
  validateSchemaPayload: (id: number, payload: string, registryUrl?: string) =>
    request<SchemaValidationResult>(
      `/schemas/${id}/validate${registryUrl ? `?registryUrl=${encodeURIComponent(registryUrl)}` : ''}`,
      {
        method: 'POST',
        body: JSON.stringify({ payload }),
      }
    ),
  registerSchema: (body: {
    schemaId?: number;
    subject?: string;
    schemaType?: string;
    schemaContent: string;
    registryUrl?: string;
  }) =>
    request<SchemaMetadata>('/schemas', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  listDlqTopics: (connectionId: string) =>
    request<DlqTopicSummary[]>(connectionPath(connectionId, '/dlq/topics')),
  inspectDlqMessages: (connectionId: string, topic: string, max?: number) =>
    request<DlqMessageInfo[]>(
      `${connectionPath(connectionId, '/dlq/messages')}?topic=${encodeURIComponent(topic)}${max ? `&max=${max}` : ''}`
    ),
  redriveDlq: (connectionId: string, body: DlqRedriveRequest) =>
    request<DlqRedriveResult>(connectionPath(connectionId, '/dlq/redrive'), {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  getMaskingConfig: () =>
    request<import('./inspectTypes').MaskingConfigResponse>('/masking/config'),
  previewMasking: (body: import('./inspectTypes').MaskingPreviewRequest) =>
    request<import('./inspectTypes').MaskingPreviewResponse>('/masking/preview', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  getTracingConfig: () =>
    request<import('./inspectTypes').TracingConfigResponse>('/tracing/config'),
  extractTraceContext: (headers: Record<string, string>) =>
    request<import('./inspectTypes').TraceContext>('/tracing/extract', {
      method: 'POST',
      body: JSON.stringify(headers),
    }),
  resolveTraceUrl: (traceId: string) =>
    request<{ traceId: string; url?: string }>(`/tracing/url?traceId=${encodeURIComponent(traceId)}`),
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
