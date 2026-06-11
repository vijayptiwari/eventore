import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const srcRoot = join(dirname(fileURLToPath(import.meta.url)));

describe('FEAT-3.2 Kinesis shard inspection UI contract', () => {
  it('AC-2/AC-4: StreamInspector shows Shards tab and hides Kafka admin for KINESIS', () => {
    const inspector = readFileSync(join(srcRoot, 'components', 'StreamInspector.tsx'), 'utf8');
    expect(inspector).toMatch(/id: 'shards'/);
    expect(inspector).toMatch(/StreamInspectorShardsTab/);
    expect(inspector).toMatch(/canShards = protocol === 'KINESIS'/);
    expect(inspector).toMatch(/adminProtocols\.includes\('KINESIS'\)/);
    expect(inspector).toMatch(/id: 'admin'.*show: protocol === 'KAFKA'/s);
  });

  it('AC-3: StreamInspectorShardsTab surfaces backend error text', () => {
    const shardsTab = readFileSync(join(srcRoot, 'components', 'StreamInspectorShardsTab.tsx'), 'utf8');
    expect(shardsTab).toMatch(/className="stream-error"/);
    expect(shardsTab).toMatch(/String\(error\)/);
    expect(shardsTab).toMatch(/shardId/);
    expect(shardsTab).toMatch(/hashKeyRange/);
    expect(shardsTab).toMatch(/sequenceNumberRange/);
  });
});

describe('FEAT-3.3 capability-driven gating contract', () => {
  it('AC-1/AC-2: tab visibility derives from hasInspectFeature only', () => {
    const inspector = readFileSync(join(srcRoot, 'components', 'StreamInspector.tsx'), 'utf8');
    expect(inspector).toMatch(/canSearch = hasInspectFeature\(feats, 'message-search'\)/);
    expect(inspector).toMatch(
      /canLag = hasInspectFeature\(feats, 'lag'\) \|\| hasInspectFeature\(feats, 'backlog'\)/,
    );
    expect(inspector).not.toMatch(/canSearch = .*protocol ===/);
    expect(inspector).not.toMatch(/canLag = .*protocol ===/);
  });

  it('AC-5: search tab catches InspectNotSupportedError with protocol banner', () => {
    const inspector = readFileSync(join(srcRoot, 'components', 'StreamInspector.tsx'), 'utf8');
    expect(inspector).toMatch(/searchMutation\.error instanceof InspectNotSupportedError/);
    expect(inspector).toMatch(/Not supported for \{protocol\}/);
  });

  it('AC-6: overview lists capability feature chips', () => {
    const overview = readFileSync(join(srcRoot, 'components', 'StreamInspectorOverviewTab.tsx'), 'utf8');
    expect(overview).toMatch(/capabilities\.features\.join/);
  });
});

describe('FEAT-3.4 RabbitMQ queue-centric UI contract', () => {
  it('AC-1/AC-3: RabbitMQ tab labels are Queues and Queue depth', () => {
    const inspector = readFileSync(join(srcRoot, 'components', 'StreamInspector.tsx'), 'utf8');
    expect(inspector).toMatch(/if \(protocol === 'RABBITMQ'\) return 'Queues'/);
    expect(inspector).toMatch(/if \(protocol === 'RABBITMQ'\) return 'Queue depth'/);
  });

  it('AC-1: Groups tab shows RabbitMQ management helper text', () => {
    const groups = readFileSync(join(srcRoot, 'components', 'StreamInspectorGroupsTab.tsx'), 'utf8');
    expect(groups).toMatch(/RabbitMQ management API/);
  });

  it('AC-5: groups tab gated by queues not subscriptions alone', () => {
    const inspector = readFileSync(join(srcRoot, 'components', 'StreamInspector.tsx'), 'utf8');
    expect(inspector).toMatch(/hasInspectFeature\(feats, 'queues'\)/);
    expect(inspector).toMatch(/hasInspectFeature\(feats, 'subscriptions'\)/);
  });
});
