import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import StreamInspectorShardsTab from './StreamInspectorShardsTab';

describe('StreamInspectorShardsTab (FEAT-3.2)', () => {
  it('AC-5: renders shard table rows from mock response', () => {
    const html = renderToStaticMarkup(
      <StreamInspectorShardsTab
        streamName="orders-stream"
        isLoading={false}
        error={undefined}
        shards={[
          {
            shardId: 'shard-000000000000',
            hashKeyRange: '0 — 100',
            sequenceNumberRange: '100 — 200',
          },
        ]}
      />,
    );

    expect(html).toContain('orders-stream');
    expect(html).toContain('shard-000000000000');
    expect(html).toContain('0 — 100');
    expect(html).toContain('100 — 200');
  });

  it('AC-3: shows backend error message instead of generic Error', () => {
    const html = renderToStaticMarkup(
      <StreamInspectorShardsTab
        streamName="missing-stream"
        isLoading={false}
        error={new Error('Stream not found in us-east-1')}
        shards={undefined}
      />,
    );

    expect(html).toContain('stream-error');
    expect(html).toContain('Stream not found in us-east-1');
  });
});
