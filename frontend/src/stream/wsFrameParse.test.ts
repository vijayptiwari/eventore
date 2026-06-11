import { describe, expect, it } from 'vitest';
import { parseWsFrame } from '../stream/wsFrameParse';

describe('parseWsFrame', () => {
  it('accepts a minimal SUBSCRIBED frame', () => {
    expect(
      parseWsFrame({
        type: 'SUBSCRIBED',
        clientStreamId: 'stream-1',
        subscriptionId: 'sub-1',
      }),
    ).toEqual({
      type: 'SUBSCRIBED',
      clientStreamId: 'stream-1',
      subscriptionId: 'sub-1',
    });
  });

  it('allows frames without clientStreamId', () => {
    expect(parseWsFrame({ type: 'HEARTBEAT' })).toEqual({ type: 'HEARTBEAT' });
  });

  it('rejects non-objects, missing type, and malformed fields', () => {
    expect(parseWsFrame(null)).toBeNull();
    expect(parseWsFrame('MESSAGE')).toBeNull();
    expect(parseWsFrame({ clientStreamId: 'x' })).toBeNull();
    expect(parseWsFrame({ type: 'ERROR', clientStreamId: 42 })).toBeNull();
    expect(parseWsFrame({ type: 'MESSAGE', clientStreamId: 'x', message: 'bad' })).toBeNull();
  });
});
