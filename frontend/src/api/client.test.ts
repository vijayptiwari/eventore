import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, canAction, PROTOCOL_DEFAULTS } from './client';

describe('api client helpers', () => {
  it('canAction checks allowed actions', () => {
    expect(canAction(['SUBSCRIBE', 'PUBLISH'], 'SUBSCRIBE')).toBe(true);
    expect(canAction(['SUBSCRIBE'], 'PUBLISH')).toBe(false);
    expect(canAction(undefined, 'SUBSCRIBE')).toBe(false);
  });

  it('defines defaults for every protocol', () => {
    const protocols = [
      'KAFKA',
      'MQTT',
      'JMS',
      'PULSAR',
      'RABBITMQ',
      'KINESIS',
      'GCP_PUBSUB',
      'AZURE_SERVICE_BUS',
    ] as const;
    for (const protocol of protocols) {
      expect(PROTOCOL_DEFAULTS[protocol].brokerUrl).toBeTruthy();
    }
  });
});

describe('connectionPath encoding', () => {
  it('encodes special characters in connection ids', async () => {
    const { api } = await import('./client');
    const originalFetch = global.fetch;
    let requestedUrl = '';
    global.fetch = async (input) => {
      requestedUrl = String(input);
      return new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } });
    };
    try {
      await api.listDestinations('conn/with/slash');
      expect(requestedUrl).toContain('/connections/conn%2Fwith%2Fslash/destinations');
    } finally {
      global.fetch = originalFetch;
    }
  });
});

describe('auth header injection', () => {
  const originalFetch = global.fetch;
  const originalConfig = window.__EVENTORE_CONFIG__;

  afterEach(() => {
    global.fetch = originalFetch;
    window.__EVENTORE_CONFIG__ = originalConfig;
    sessionStorage.clear();
  });

  it('authHeaders returns empty object when no token is configured', async () => {
    window.__EVENTORE_CONFIG__ = {};
    const { authHeaders } = await import('./client');
    expect(authHeaders()).toEqual({});
  });

  it('authHeaders returns Bearer authorization when token is configured', async () => {
    window.__EVENTORE_CONFIG__ = { apiToken: 'secret-token' };
    const { authHeaders } = await import('./client');
    expect(authHeaders()).toEqual({
      Authorization: 'Bearer secret-token',
    });
  });

  it('request attaches auth headers when apiToken is configured', async () => {
    window.__EVENTORE_CONFIG__ = { apiToken: 'my-token' };
    let capturedHeaders: HeadersInit | undefined;
    global.fetch = async (_input, init) => {
      capturedHeaders = init?.headers;
      return new Response(JSON.stringify({}), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    };
    const { api } = await import('./client');
    await api.getConfig();
    expect(capturedHeaders).toMatchObject({
      Authorization: 'Bearer my-token',
      'Content-Type': 'application/json',
    });
  });

  it('request omits auth headers when apiToken is not configured', async () => {
    window.__EVENTORE_CONFIG__ = {};
    sessionStorage.clear();
    let capturedHeaders: HeadersInit | undefined;
    global.fetch = async (_input, init) => {
      capturedHeaders = init?.headers;
      return new Response(JSON.stringify({}), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    };
    const { api } = await import('./client');
    await api.getConfig();
    expect(capturedHeaders).toEqual({
      'Content-Type': 'application/json',
    });
  });

  it('reads apiToken from sessionStorage when not injected in window config', async () => {
    window.__EVENTORE_CONFIG__ = {};
    sessionStorage.setItem('eventore.apiToken', 'stored-token');
    const { authHeaders } = await import('./client');
    expect(authHeaders()).toEqual({
      Authorization: 'Bearer stored-token',
    });
  });
});

describe('request error handling', () => {
  const originalFetch = global.fetch;

  afterEach(() => {
    global.fetch = originalFetch;
    vi.useRealTimers();
  });

  it('throws the backend error message from a JSON problem body', async () => {
    global.fetch = async () =>
      new Response(JSON.stringify({ error: 'broker unreachable' }), { status: 500 });
    await expect(api.getConfig()).rejects.toThrow('broker unreachable');
  });

  it('falls back to the raw body for a non-JSON error response', async () => {
    global.fetch = async () => new Response('<html>Bad Gateway</html>', { status: 502 });
    await expect(api.getConfig()).rejects.toThrow('<html>Bad Gateway</html>');
  });

  it('falls back to status text when the error body is empty', async () => {
    global.fetch = async () =>
      new Response('', { status: 503, statusText: 'Service Unavailable' });
    await expect(api.getConfig()).rejects.toThrow('Service Unavailable');
  });

  it('rejects with a timeout error when the backend never responds', async () => {
    vi.useFakeTimers();
    global.fetch = ((_input: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () =>
          reject(new DOMException('The operation was aborted.', 'AbortError')),
        );
      })) as typeof fetch;

    const pending = api.getConfig();
    const assertion = expect(pending).rejects.toThrow(/timed out after 30000ms/);
    await vi.advanceTimersByTimeAsync(30_001);
    await assertion;
  });
});
