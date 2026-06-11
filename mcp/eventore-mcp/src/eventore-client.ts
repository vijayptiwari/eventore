import type { ProtocolType } from './protocol-types.js';

export type { ProtocolType };

export interface ConnectionProfile {
  id?: string;
  name: string;
  protocol: ProtocolType;
  brokerUrl: string;
  properties?: Record<string, string>;
  credentials?: Record<string, string>;
}

export interface ControlPlaneUiCascade {
  connectionProtocols?: string[];
  inspectProtocols?: string[];
  adminProtocols?: string[];
  platformFilterProtocols?: string[];
}

export interface ControlPlaneSnapshot {
  revision: number;
  providers?: unknown[];
  activeProtocols: string[];
  openApiStreams?: string[];
  uiCascade: ControlPlaneUiCascade;
}

export interface ControlPlaneView {
  revision: number;
  uiCascade: ControlPlaneUiCascade;
  openApiStreams?: string[];
}

export interface AppConfig {
  deploymentMode: string;
  allowedActions: string[];
  supportedProtocols: ProtocolType[];
  loadedModules?: string[];
  controlPlane?: ControlPlaneView;
}

export interface TopicRef {
  name: string;
  type: string;
  protocol: ProtocolType;
}

export interface UnifiedMessage {
  id: string;
  destination: string;
  headers: Record<string, string>;
  payload: string;
  timestamp: string;
  protocol: ProtocolType;
}

export class EventoreClient {
  constructor(
    private readonly baseUrl: string,
    private readonly apiToken?: string,
  ) {}

  private authHeaders(): Record<string, string> {
    const token = this.apiToken?.trim();
    if (!token) {
      return {};
    }
    return { Authorization: `Bearer ${token}` };
  }

  private apiOrigin(): string {
    return this.baseUrl.replace(/\/api\/v1\/?$/, '').replace(/\/$/, '');
  }

  private resolveSseUrl(subscriptionId: string, sseUrl?: string): string {
    if (sseUrl?.trim()) {
      const trimmed = sseUrl.trim();
      if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
        return trimmed;
      }
      return `${this.apiOrigin()}${trimmed.startsWith('/') ? trimmed : `/${trimmed}`}`;
    }
    return `${this.apiOrigin()}/api/v1/stream/${subscriptionId}`;
  }

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const url = `${this.baseUrl.replace(/\/$/, '')}${path}`;
    const res = await fetch(url, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...this.authHeaders(),
        ...(init?.headers ?? {}),
      },
    });
    if (!res.ok) {
      const body = await res.text();
      throw new Error(`${res.status} ${res.statusText}: ${body}`);
    }
    if (res.status === 204) {
      return undefined as T;
    }
    return res.json() as Promise<T>;
  }

  getConfig() {
    return this.request<AppConfig>('/config');
  }

  getControlPlane() {
    return this.request<ControlPlaneSnapshot>('/control/plane');
  }

  listProviders() {
    return this.request<unknown[]>('/control/providers');
  }

  getProvider(protocol: ProtocolType) {
    return this.request<unknown>(`/control/providers/${protocol}`);
  }

  getProviderStatus(protocol: ProtocolType) {
    return this.request<Record<string, unknown>>(`/control/providers/${protocol}/status`);
  }

  registerProvider(protocol: ProtocolType) {
    return this.request<unknown>(`/control/providers/${protocol}/register`, { method: 'POST' });
  }

  deregisterProvider(protocol: ProtocolType) {
    return this.request<unknown>(`/control/providers/${protocol}/register`, { method: 'DELETE' });
  }

  listConnections() {
    return this.request<ConnectionProfile[]>('/connections');
  }

  getConnection(id: string) {
    return this.request<ConnectionProfile>(`/connections/${id}`);
  }

  createConnection(profile: ConnectionProfile) {
    return this.request<ConnectionProfile>('/connections', {
      method: 'POST',
      body: JSON.stringify(profile),
    });
  }

  updateConnection(id: string, profile: ConnectionProfile) {
    return this.request<ConnectionProfile>(`/connections/${id}`, {
      method: 'PUT',
      body: JSON.stringify(profile),
    });
  }

  deleteConnection(id: string) {
    return this.request<void>(`/connections/${id}`, { method: 'DELETE' });
  }

  validateConnection(id: string) {
    return this.request<{ status: string }>(`/connections/${id}/validate`, {
      method: 'POST',
    });
  }

  listDestinations(connectionId: string) {
    return this.request<TopicRef[]>(`/connections/${connectionId}/destinations`);
  }

  publish(
    connectionId: string,
    body: { destination: string; payload: string; headers?: Record<string, string> },
  ) {
    return this.request<{ status: string }>(`/connections/${connectionId}/publish`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  }

  subscribe(
    connectionId: string,
    body: { destination: string; consumerGroup?: string; options?: Record<string, string> },
  ) {
    return this.request<{ subscriptionId: string; sseUrl: string }>(
      `/connections/${connectionId}/subscribe`,
      { method: 'POST', body: JSON.stringify(body) },
    );
  }

  unsubscribe(connectionId: string, subscriptionId: string) {
    return this.request<{ status: string }>(
      `/connections/${connectionId}/subscribe/${subscriptionId}`,
      { method: 'DELETE' },
    );
  }

  inspectCapabilities(connectionId: string) {
    return this.request<unknown>(`/connections/${connectionId}/inspect/capabilities`);
  }

  inspectTopics(connectionId: string, filter?: string) {
    const q = filter ? `?filter=${encodeURIComponent(filter)}` : '';
    return this.request<unknown>(`/connections/${connectionId}/inspect/topics${q}`);
  }

  inspectTopic(connectionId: string, topic: string) {
    return this.request<unknown>(
      `/connections/${connectionId}/inspect/topics/${encodeURIComponent(topic)}`,
    );
  }

  inspectCluster(connectionId: string) {
    return this.request<unknown>(`/connections/${connectionId}/inspect/cluster`);
  }

  inspectConsumerGroups(connectionId: string) {
    return this.request<unknown>(`/connections/${connectionId}/inspect/consumer-groups`);
  }

  inspectLag(connectionId: string, groupId: string, topic?: string) {
    const q = `?groupId=${encodeURIComponent(groupId)}${topic ? `&topic=${encodeURIComponent(topic)}` : ''}`;
    return this.request<unknown>(`/connections/${connectionId}/inspect/lag${q}`);
  }

  inspectSearch(connectionId: string, body: Record<string, unknown>) {
    return this.request<unknown>(`/connections/${connectionId}/inspect/search`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  }

  kafkaPublish(
    connectionId: string,
    body: { destination: string; payload: string; headers?: Record<string, string> },
    flush = false,
  ) {
    const q = flush ? '?flush=true' : '';
    return this.request<unknown>(`/connections/${connectionId}/kafka/publish${q}`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  }

  kafkaCreateTopic(connectionId: string, body: Record<string, unknown>) {
    return this.request<unknown>(`/connections/${connectionId}/kafka/topics`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  }

  kafkaDeleteTopic(connectionId: string, topic: string) {
    return this.request<unknown>(
      `/connections/${connectionId}/kafka/topics/${encodeURIComponent(topic)}`,
      { method: 'DELETE' },
    );
  }

  kinesisListShards(connectionId: string, streamName: string) {
    return this.request<unknown>(
      `/connections/${connectionId}/kinesis/streams/${encodeURIComponent(streamName)}/shards`,
    );
  }

  kafkaListAcls(connectionId: string, resourceType?: string, resourceName?: string) {
    const params = new URLSearchParams();
    if (resourceType) params.set('resourceType', resourceType);
    if (resourceName) params.set('resourceName', resourceName);
    const q = params.toString() ? `?${params}` : '';
    return this.request<unknown>(`/connections/${connectionId}/kafka/acls${q}`);
  }

  kafkaCreateAcl(connectionId: string, entry: Record<string, unknown>) {
    return this.request<unknown>(`/connections/${connectionId}/kafka/acls`, {
      method: 'POST',
      body: JSON.stringify(entry),
    });
  }

  /** Poll SSE stream for a bounded time and collect MESSAGE frames. */
  async consumeMessages(
    subscriptionId: string,
    options: { maxMessages?: number; timeoutMs?: number; sseUrl?: string } = {},
  ): Promise<UnifiedMessage[]> {
    const maxMessages = options.maxMessages ?? 20;
    const timeoutMs = options.timeoutMs ?? 10_000;
    const sseUrl = this.resolveSseUrl(subscriptionId, options.sseUrl);

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    const messages: UnifiedMessage[] = [];

    try {
      const res = await fetch(sseUrl, {
        headers: {
          Accept: 'text/event-stream',
          ...this.authHeaders(),
        },
        signal: controller.signal,
      });
      if (!res.ok || !res.body) {
        const body = await res.text().catch(() => '');
        throw new Error(`SSE failed: ${res.status}${body ? ` ${body}` : ''}`);
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (messages.length < maxMessages) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split('\n\n');
        buffer = parts.pop() ?? '';
        for (const part of parts) {
          const dataLine = part.split('\n').find((l) => l.startsWith('data:'));
          if (!dataLine) continue;
          try {
            const json = JSON.parse(dataLine.slice(5).trim()) as {
              type?: string;
              message?: UnifiedMessage;
            };
            if (json.type === 'MESSAGE' && json.message) {
              messages.push(json.message);
            }
          } catch {
            // ignore malformed chunks
          }
        }
      }
    } catch (e) {
      if (messages.length === 0 && !(e instanceof Error && e.name === 'AbortError')) {
        throw e;
      }
    } finally {
      clearTimeout(timer);
    }

    return messages;
  }
}
