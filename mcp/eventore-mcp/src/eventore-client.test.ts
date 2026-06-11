import assert from 'node:assert/strict';
import { afterEach, beforeEach, describe, it, mock } from 'node:test';
import { EventoreClient, type UnifiedMessage } from './eventore-client.js';

function sseBody(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    },
  });
}

describe('EventoreClient.consumeMessages', () => {
  let originalFetch: typeof fetch;

  beforeEach(() => {
    originalFetch = globalThis.fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    mock.restoreAll();
  });

  it('AC-1: resolves relative sseUrl with connectionId against baseUrl', async () => {
    let capturedUrl = '';
    globalThis.fetch = (async (input, init) => {
      capturedUrl = String(input);
      return new Response(sseBody([]), {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
      });
    }) as typeof fetch;

    const client = new EventoreClient('http://localhost:8080/api/v1');
    await client.consumeMessages('sub-ignored', {
      sseUrl: '/api/v1/stream/sub-1?connectionId=conn-abc',
      timeoutMs: 50,
      maxMessages: 1,
    });

    assert.equal(
      capturedUrl,
      'http://localhost:8080/api/v1/stream/sub-1?connectionId=conn-abc',
    );
    assert.ok(!capturedUrl.endsWith('/api/v1/stream/sub-ignored'));
  });

  it('AC-1: passes through absolute sseUrl unchanged', async () => {
    let capturedUrl = '';
    globalThis.fetch = (async (input) => {
      capturedUrl = String(input);
      return new Response(sseBody([]), {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
      });
    }) as typeof fetch;

    const client = new EventoreClient('http://localhost:8080/api/v1');
    const absolute = 'https://proxy.example/api/v1/stream/x?connectionId=y';
    await client.consumeMessages('sub-ignored', { sseUrl: absolute, timeoutMs: 50, maxMessages: 1 });

    assert.equal(capturedUrl, absolute);
  });

  it('AC-2: attaches Authorization Bearer header on SSE fetch when token is set', async () => {
    let capturedHeaders: HeadersInit | undefined;
    globalThis.fetch = (async (_input, init) => {
      capturedHeaders = init?.headers;
      return new Response(sseBody([]), {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
      });
    }) as typeof fetch;

    const client = new EventoreClient('http://localhost:8080/api/v1', 'my-secret');
    await client.consumeMessages('sub-1', { timeoutMs: 50, maxMessages: 1 });

    assert.deepEqual(capturedHeaders, {
      Accept: 'text/event-stream',
      Authorization: 'Bearer my-secret',
    });
  });

  it('AC-3: throws with status when SSE returns 401 without token', async () => {
    globalThis.fetch = (async () =>
      new Response('Unauthorized', { status: 401 })) as typeof fetch;

    const client = new EventoreClient('http://localhost:8080/api/v1');
    await assert.rejects(
      () => client.consumeMessages('sub-1', { timeoutMs: 50, maxMessages: 1 }),
      (err: Error) => {
        assert.match(err.message, /401/);
        return true;
      },
    );
  });

  it('AC-5: omits Authorization when no api token configured', async () => {
    let capturedHeaders: HeadersInit | undefined;
    globalThis.fetch = (async (_input, init) => {
      capturedHeaders = init?.headers;
      return new Response(sseBody([]), {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
      });
    }) as typeof fetch;

    const client = new EventoreClient('http://localhost:8080/api/v1');
    await client.consumeMessages('sub-1', { timeoutMs: 50, maxMessages: 1 });

    assert.deepEqual(capturedHeaders, { Accept: 'text/event-stream' });
    assert.ok(!('Authorization' in (capturedHeaders as Record<string, string>)));
  });

  it('AC-4: collects MESSAGE SSE frames from stream body', async () => {
    const sample: UnifiedMessage = {
      id: 'm1',
      destination: 'topic-a',
      headers: {},
      payload: 'hello',
      timestamp: '2026-06-11T00:00:00Z',
      protocol: 'KAFKA',
    };
    const frame = `data: ${JSON.stringify({ type: 'MESSAGE', message: sample })}\n\n`;

    globalThis.fetch = (async () =>
      new Response(sseBody([frame]), {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
      })) as typeof fetch;

    const client = new EventoreClient('http://localhost:8080/api/v1');
    const messages = await client.consumeMessages('sub-1', {
      sseUrl: '/api/v1/stream/sub-1?connectionId=conn-1',
      timeoutMs: 500,
      maxMessages: 5,
    });

    assert.equal(messages.length, 1);
    assert.equal(messages[0]?.payload, 'hello');
  });
});

describe('EventoreClient.kinesisListShards (FEAT-4.2)', () => {
  let originalFetch: typeof fetch;

  beforeEach(() => {
    originalFetch = globalThis.fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    mock.restoreAll();
  });

  it('AC-1/AC-4: GET kinesis shards path with auth headers when token set', async () => {
    let capturedUrl = '';
    let capturedHeaders: HeadersInit | undefined;
    globalThis.fetch = (async (input, init) => {
      capturedUrl = String(input);
      capturedHeaders = init?.headers;
      return new Response(JSON.stringify([{ shardId: 'shard-1', hashKeyRange: '0-99' }]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }) as typeof fetch;

    const client = new EventoreClient('http://localhost:8080/api/v1', 'shard-token');
    const shards = await client.kinesisListShards('conn-1', 'events');

    assert.equal(
      capturedUrl,
      'http://localhost:8080/api/v1/connections/conn-1/kinesis/streams/events/shards',
    );
    assert.deepEqual(capturedHeaders, {
      'Content-Type': 'application/json',
      Authorization: 'Bearer shard-token',
    });
    assert.equal((shards as Array<{ shardId: string }>)[0]?.shardId, 'shard-1');
  });
});
