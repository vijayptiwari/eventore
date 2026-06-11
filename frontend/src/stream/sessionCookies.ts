import type { PersistedStreamSession } from './types';

const SESSION_KEY = 'eventore_stream_sessions_v1';
const ACTIVE_KEY = 'eventore_active_stream_v1';
const COOKIE_PREFIX = 'eventore_streams_';
const COOKIE_COUNT = 'eventore_streams_n';
const COOKIE_DAYS = 30;
const CHUNK_SIZE = 2800;
const MAX_STREAMS = 40;

/**
 * localStorage throws in some privacy modes / blocked-storage contexts;
 * this adapter degrades gracefully so callers can fall back to cookies.
 */
const safeLocalStorage = {
  get(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  },
  set(key: string, value: string): boolean {
    try {
      localStorage.setItem(key, value);
      return true;
    } catch {
      return false;
    }
  },
  remove(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch {
      // storage unavailable — nothing to remove
    }
  },
};

function setCookie(name: string, value: string): void {
  const maxAge = COOKIE_DAYS * 86400;
  const secure = window.location.protocol === 'https:' ? ';Secure' : '';
  document.cookie = `${name}=${encodeURIComponent(value)};path=/;max-age=${maxAge};SameSite=Lax${secure}`;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${escapeRegExp(name)}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

function deleteCookie(name: string): void {
  document.cookie = `${name}=;path=/;max-age=0;SameSite=Lax`;
}

function clearSessionChunks(): void {
  const count = parseInt(getCookie(COOKIE_COUNT) ?? '0', 10);
  deleteCookie(COOKIE_COUNT);
  for (let i = 0; i < count; i++) {
    deleteCookie(`${COOKIE_PREFIX}${i}`);
  }
}

function loadFromCookies(): PersistedStreamSession[] {
  try {
    const n = parseInt(getCookie(COOKIE_COUNT) ?? '0', 10);
    if (n <= 0) return [];
    let json = '';
    for (let i = 0; i < n; i++) {
      const part = getCookie(`${COOKIE_PREFIX}${i}`);
      if (part) json += part;
    }
    if (!json) return [];
    const parsed = JSON.parse(json) as PersistedStreamSession[];
    return Array.isArray(parsed) ? parsed.slice(0, MAX_STREAMS) : [];
  } catch {
    return [];
  }
}

function saveToCookies(sessions: PersistedStreamSession[]): void {
  const trimmed = sessions.slice(0, MAX_STREAMS);
  const json = JSON.stringify(trimmed);
  clearSessionChunks();
  if (!json || json === '[]') return;
  const chunks = Math.ceil(json.length / CHUNK_SIZE);
  if (chunks > 12) {
    safeLocalStorage.set(SESSION_KEY, json);
    return;
  }
  for (let i = 0; i < chunks; i++) {
    setCookie(`${COOKIE_PREFIX}${i}`, json.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE));
  }
  setCookie(COOKIE_COUNT, String(chunks));
}

export function loadStreamSessionsFromStorage(): PersistedStreamSession[] {
  try {
    const ls = safeLocalStorage.get(SESSION_KEY);
    if (ls) {
      const parsed = JSON.parse(ls) as PersistedStreamSession[];
      if (Array.isArray(parsed)) return parsed.slice(0, MAX_STREAMS);
    }
  } catch {
    // fallback cookies
  }
  return loadFromCookies();
}

export function saveStreamSessionsToStorage(sessions: PersistedStreamSession[]): void {
  const trimmed = sessions.slice(0, MAX_STREAMS);
  safeLocalStorage.set(SESSION_KEY, JSON.stringify(trimmed));
  saveToCookies(trimmed);
}

export function loadActiveStreamIdFromStorage(): string | null {
  return safeLocalStorage.get(ACTIVE_KEY) ?? getCookie('eventore_active_stream');
}

export function saveActiveStreamIdToStorage(id: string | null): void {
  if (id) {
    safeLocalStorage.set(ACTIVE_KEY, id);
    setCookie('eventore_active_stream', id);
  } else {
    safeLocalStorage.remove(ACTIVE_KEY);
    deleteCookie('eventore_active_stream');
  }
}

export function toPersisted(session: {
  id: string;
  connectionId: string;
  connectionName: string;
  protocol: PersistedStreamSession['protocol'];
  destination: string;
  consumerGroup?: string;
  status: PersistedStreamSession['status'];
  subscriptionId?: string;
  lastError?: string;
  messageCount: number;
  createdAt: number;
  updatedAt: number;
}): PersistedStreamSession {
  return {
    id: session.id,
    connectionId: session.connectionId,
    connectionName: session.connectionName,
    protocol: session.protocol,
    destination: session.destination,
    consumerGroup: session.consumerGroup,
    status: session.status,
    subscriptionId: session.subscriptionId,
    lastError: session.lastError,
    messageCount: session.messageCount,
    createdAt: session.createdAt,
    updatedAt: session.updatedAt,
  };
}
