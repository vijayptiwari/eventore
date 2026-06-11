import type { StreamFrame } from './types';

export function parseWsFrame(raw: unknown): StreamFrame | null {
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
    return null;
  }
  const obj = raw as Record<string, unknown>;
  if (typeof obj.type !== 'string' || obj.type.length === 0) {
    return null;
  }
  if (obj.clientStreamId !== undefined) {
    if (typeof obj.clientStreamId !== 'string' || obj.clientStreamId.length === 0) {
      return null;
    }
  }
  if (obj.subscriptionId !== undefined && typeof obj.subscriptionId !== 'string') {
    return null;
  }
  if (obj.detail !== undefined && typeof obj.detail !== 'string') {
    return null;
  }
  if (obj.expiresAt !== undefined && (typeof obj.expiresAt !== 'number' || !Number.isFinite(obj.expiresAt))) {
    return null;
  }
  if (obj.message !== undefined && (typeof obj.message !== 'object' || obj.message === null || Array.isArray(obj.message))) {
    return null;
  }
  return raw as StreamFrame;
}
