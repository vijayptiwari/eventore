import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, it } from 'node:test';

const packageRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const toolsSource = readFileSync(join(packageRoot, 'src', 'tools.ts'), 'utf8');
const clientSource = readFileSync(join(packageRoot, 'src', 'eventore-client.ts'), 'utf8');
const readmeSource = readFileSync(join(packageRoot, 'README.md'), 'utf8');

describe('FEAT-4.1 generic inspect MCP tools contract', () => {
  it('AC-1: inspect tools registered with Zod schemas', () => {
    assert.match(toolsSource, /'eventore_inspect_capabilities'/);
    assert.match(toolsSource, /connectionId: z\.string\(\)/);
    assert.match(toolsSource, /'eventore_inspect_topics'/);
    assert.match(toolsSource, /filter: z\.string\(\)\.optional\(\)/);
    assert.match(toolsSource, /'eventore_inspect_topic'/);
    assert.match(toolsSource, /topic: z\.string\(\)/);
  });

  it('AC-2: eventore_inspect_capabilities calls inspect/capabilities REST path', () => {
    assert.match(clientSource, /inspectCapabilities\(connectionId: string\)/);
    assert.match(clientSource, /\/inspect\/capabilities/);
  });

  it('AC-3: inspect topics and topic tools map to REST paths with filter/topic args', () => {
    assert.match(clientSource, /inspectTopics\(connectionId: string, filter\?: string\)/);
    assert.match(clientSource, /inspectTopic\(connectionId: string, topic: string\)/);
    assert.match(toolsSource, /client\.inspectTopics\(args\.connectionId, args\.filter\)/);
    assert.match(toolsSource, /client\.inspectTopic\(args\.connectionId, args\.topic\)/);
  });

  it('AC-4: 501 / EVT-1501 errors return MCP text instead of uncaught throw', () => {
    assert.match(toolsSource, /function inspectToolResultFromError/);
    assert.match(toolsSource, /501 Not implemented/);
    assert.match(toolsSource, /EVT-1501/);
    assert.match(toolsSource, /inspectToolResultFromError\(error\)/);
  });

  it('AC-5: eventore_get_config description references control plane cascade', () => {
    assert.match(toolsSource, /'eventore_get_config'/);
    assert.match(toolsSource, /controlPlane UI cascade/);
    assert.match(toolsSource, /inspectProtocols/);
  });

  it('AC-6: README tool table documents new inspect tools', () => {
    assert.match(readmeSource, /eventore_inspect_capabilities/);
    assert.match(readmeSource, /eventore_inspect_topics/);
    assert.match(readmeSource, /eventore_inspect_topic/);
  });
});

describe('FEAT-4.2 Kinesis list shards MCP tool contract', () => {
  it('AC-1: tool args connectionId and streamName call kinesis shards REST path', () => {
    assert.match(toolsSource, /'eventore_kinesis_list_shards'/);
    assert.match(toolsSource, /streamName: z\.string\(\)/);
    assert.match(toolsSource, /client\.kinesisListShards\(args\.connectionId, args\.streamName\)/);
    assert.match(clientSource, /kinesisListShards\(connectionId: string, streamName: string\)/);
    assert.match(clientSource, /\/kinesis\/streams\/.*\/shards/);
  });

  it('AC-2: tool description states KINESIS adminProtocols prerequisite', () => {
    assert.match(toolsSource, /requires KINESIS in adminProtocols/);
  });

  it('AC-3: successful response uses JSON pretty-print via inspectToolResult', () => {
    assert.match(toolsSource, /return inspectToolResult\(data\)/);
    assert.match(toolsSource, /JSON\.stringify\(data, null, 2\)/);
  });
});
