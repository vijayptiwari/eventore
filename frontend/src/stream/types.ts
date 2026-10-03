import type { ProtocolType, UnifiedMessage } from '../api/types';

export type StreamStatus = 'idle' | 'connecting' | 'active' | 'error' | 'stopped';

/** Persisted to cookies (no live messages). */
export interface PersistedStreamSession {
  id: string;
  connectionId: string;
  connectionName: string;
  protocol: ProtocolType;
  destination: string;
  consumerGroup?: string;
  status: StreamStatus;
  subscriptionId?: string;
  lastError?: string;
  messageCount: number;
  createdAt: number;
  updatedAt: number;
}

/** Runtime session with in-memory messages. */
export interface LiveStreamSession extends PersistedStreamSession {
  messages: UnifiedMessage[];
}

export type LiveViewDurationMinutes = 1 | 2 | 5 | 10;
export type ReplayMode = 'LATEST' | 'EARLIEST' | 'TIMESTAMP' | 'OFFSET';

export interface ReplayOptions {
  replayMode?: ReplayMode;
  replayTimestamp?: string;
  replayOffset?: number;
}

export interface LiveViewState {
  active: boolean;
  status: 'idle' | 'connecting' | 'active' | 'expired' | 'error';
  subscriptionId?: string;
  topics: string[];
  headerRegex: string;
  bodyRegex: string;
  durationMinutes: LiveViewDurationMinutes;
  replayMode?: ReplayMode;
  replayTimestamp?: string;
  replayOffset?: number;
  expiresAt?: number;
  messages: UnifiedMessage[];
  lastError?: string;
}

export interface StreamFrame {
  type: string;
  subscriptionId?: string;
  clientStreamId?: string;
  message?: UnifiedMessage;
  detail?: string;
  expiresAt?: number;
}
