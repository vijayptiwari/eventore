import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const srcRoot = join(dirname(fileURLToPath(import.meta.url)));

describe('REQ-106 Schema Registry & Avro Decoder UI contract', () => {
  it('AC-1: MessagePayloadViewer renders Schema ID and schema type badge', () => {
    const viewer = readFileSync(join(srcRoot, 'components', 'MessagePayloadViewer.tsx'), 'utf8');
    expect(viewer).toMatch(/x-eventore-schema-id/);
    expect(viewer).toMatch(/x-eventore-schema-type/);
    expect(viewer).toMatch(/schema-badge/);
    expect(viewer).toMatch(/Schema #/);
  });

  it('AC-2: LiveViewPanel integrates MessagePayloadViewer', () => {
    const liveView = readFileSync(join(srcRoot, 'components', 'LiveViewPanel.tsx'), 'utf8');
    expect(liveView).toMatch(/import MessagePayloadViewer from '\.\/MessagePayloadViewer'/);
    expect(liveView).toMatch(/<MessagePayloadViewer payload=\{m\.payload\} headers=\{m\.headers\} \/>/);
  });

  it('AC-3: StreamInspector tabs integrate MessagePayloadViewer', () => {
    const topicsTab = readFileSync(join(srcRoot, 'components', 'StreamInspectorTopicsTab.tsx'), 'utf8');
    expect(topicsTab).toMatch(/MessagePayloadViewer/);
    expect(topicsTab).toMatch(/<MessagePayloadViewer payload=\{m\.payload\} headers=\{m\.headers\} \/>/);

    const searchTab = readFileSync(join(srcRoot, 'components', 'StreamInspectorSearchTab.tsx'), 'utf8');
    expect(searchTab).toMatch(/MessagePayloadViewer/);
    expect(searchTab).toMatch(/<MessagePayloadViewer payload=\{m\.payload\} headers=\{m\.headers\} \/>/);
  });

  it('AC-4: Kafka extra fields include schemaRegistryUrl in connection form', () => {
    const shared = readFileSync(join(srcRoot, 'connections', 'connectionFormShared.ts'), 'utf8');
    expect(shared).toMatch(/KAFKA:\s*\[/);
    expect(shared).toMatch(/schemaRegistryUrl/);
    expect(shared).toMatch(/Schema Registry URL/);
  });
});
