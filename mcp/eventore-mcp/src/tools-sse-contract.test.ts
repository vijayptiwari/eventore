import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, it } from 'node:test';

const packageRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const toolsSource = readFileSync(join(packageRoot, 'src', 'tools.ts'), 'utf8');
const readmeSource = readFileSync(join(packageRoot, 'README.md'), 'utf8');

describe('tools.ts SSE wiring contract', () => {
  it('AC-6: eventore_consume_messages passes sseUrl from subscribe response', () => {
    const consumeBlock = toolsSource.slice(
      toolsSource.indexOf("'eventore_consume_messages'"),
      toolsSource.indexOf("'eventore_protocol_guide'"),
    );
    assert.match(consumeBlock, /sseUrl:\s*sub\.sseUrl/);
  });

  it('AC-6: eventore_quick_probe passes sseUrl when consuming samples', () => {
    const probeBlock = toolsSource.slice(
      toolsSource.indexOf("'eventore_quick_probe'"),
      toolsSource.indexOf("'eventore_inspect_cluster'"),
    );
    assert.match(probeBlock, /sseUrl:\s*sub\.sseUrl/);
  });
});

describe('MCP README auth documentation', () => {
  it('AC-7: documents EVENTORE_API_TOKEN for REST and SSE consume', () => {
    assert.match(readmeSource, /EVENTORE_API_TOKEN/);
    assert.match(readmeSource, /SSE consume/i);
    assert.match(readmeSource, /Authorization: Bearer/i);
  });
});
