import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  loadActiveStreamIdFromStorage,
  loadStreamSessionsFromStorage,
  saveActiveStreamIdToStorage,
  saveStreamSessionsToStorage,
  toPersisted,
} from './sessionCookies';
import type { PersistedStreamSession } from './types';

describe('sessionCookies', () => {
  beforeEach(() => {
    localStorage.clear();
    document.cookie = 'eventore_streams_n=;path=/;max-age=0';
    document.cookie = 'eventore_active_stream=;path=/;max-age=0';
  });

  it('round-trips sessions through localStorage', () => {
    const sessions: PersistedStreamSession[] = [
      toPersisted({
        id: 's1',
        connectionId: 'conn-1',
        connectionName: 'Kafka dev',
        protocol: 'KAFKA',
        destination: 'orders',
        status: 'idle',
        messageCount: 0,
        createdAt: 1,
        updatedAt: 2,
      }),
    ];

    saveStreamSessionsToStorage(sessions);
    expect(loadStreamSessionsFromStorage()).toEqual(sessions);
  });

  it('stores active stream id', () => {
    saveActiveStreamIdToStorage('stream-1');
    expect(loadActiveStreamIdFromStorage()).toBe('stream-1');
    saveActiveStreamIdToStorage(null);
    expect(loadActiveStreamIdFromStorage()).toBeNull();
  });

  it('returns empty array for corrupt cookie payload', () => {
    document.cookie = 'eventore_streams_n=1;path=/';
    document.cookie = `${encodeURIComponent('eventore_streams_0')}=${encodeURIComponent('{bad json')};path=/`;
    expect(loadStreamSessionsFromStorage()).toEqual([]);
  });

  describe('localStorage unavailable (privacy mode)', () => {
    beforeEach(() => {
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('storage disabled');
      });
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('storage disabled');
      });
      vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
        throw new Error('storage disabled');
      });
    });

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('round-trips sessions through the cookie fallback without throwing', () => {
      const sessions: PersistedStreamSession[] = [
        toPersisted({
          id: 's2',
          connectionId: 'conn-2',
          connectionName: 'Rabbit dev',
          protocol: 'RABBITMQ',
          destination: 'jobs',
          status: 'idle',
          messageCount: 3,
          createdAt: 1,
          updatedAt: 2,
        }),
      ];

      expect(() => saveStreamSessionsToStorage(sessions)).not.toThrow();
      expect(loadStreamSessionsFromStorage()).toEqual(sessions);
    });

    it('round-trips the active stream id through the cookie fallback', () => {
      expect(() => saveActiveStreamIdToStorage('stream-2')).not.toThrow();
      expect(loadActiveStreamIdFromStorage()).toBe('stream-2');
      expect(() => saveActiveStreamIdToStorage(null)).not.toThrow();
      expect(loadActiveStreamIdFromStorage()).toBeNull();
    });
  });
});
