import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, it } from 'node:test';
import { EventoreClient } from './eventore-client.js';

const packageRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const toolsSource = readFileSync(join(packageRoot, 'src', 'tools.ts'), 'utf8');
const promptsSource = readFileSync(join(packageRoot, 'src', 'prompts.ts'), 'utf8');
const clientSource = readFileSync(join(packageRoot, 'src', 'eventore-client.ts'), 'utf8');
const readmeSource = readFileSync(join(packageRoot, 'README.md'), 'utf8');

describe('REQ-113 Autonomous Incident Triage MCP Tooling Contract', () => {
  it('AC-1: eventore_triage_incident tool is registered with expected schema', () => {
    assert.match(toolsSource, /'eventore_triage_incident'/);
    assert.match(toolsSource, /connectionId: z\.string\(\)/);
    assert.match(toolsSource, /destination: z\.string\(\)\.optional\(\)/);
    assert.match(toolsSource, /includeDlq: z\.boolean\(\)\.optional\(\)/);
    assert.match(toolsSource, /includeConsumerLag: z\.boolean\(\)\.optional\(\)/);
    assert.match(toolsSource, /maxErrors: z\.number\(\)\.optional\(\)/);
    assert.match(toolsSource, /client\.triageIncident\(args\.connectionId/);
  });

  it('AC-2: eventore_incident_triage prompt is registered with RCA playbook', () => {
    assert.match(promptsSource, /'eventore_incident_triage'/);
    assert.match(promptsSource, /Autonomous Incident Triage & Root Cause Analysis/);
    assert.match(promptsSource, /eventore_triage_incident/);
    assert.match(promptsSource, /Remediation Plan/);
  });

  it('AC-3: EventoreClient exposes DLQ endpoints and triageIncident method', () => {
    assert.match(clientSource, /listDlqTopics\(connectionId: string\)/);
    assert.match(clientSource, /inspectDlqMessages\(connectionId: string, topic: string/);
    assert.match(clientSource, /triageIncident\(connectionId: string/);
    assert.match(clientSource, /\/dlq\/topics/);
    assert.match(clientSource, /\/dlq\/messages/);
  });

  it('AC-4: triageIncident handles healthy cluster state with normal latency', async () => {
    const client = new EventoreClient('http://localhost:8080/api/v1');
    (client as any).inspectCluster = async () => ({ clusterId: 'test-cluster', brokers: 3 });
    (client as any).inspectConsumerGroups = async () => [{ groupId: 'test-group' }];
    (client as any).inspectLag = async () => [{ partition: 0, lag: 0 }];
    (client as any).listDlqTopics = async () => [];

    const report = await client.triageIncident('conn-test-1');
    assert.equal(report.verdict, 'HEALTHY');
    assert.equal(report.cluster.reachable, true);
    assert.equal(report.lagAnalysis?.skewDetected, false);
    assert.equal(report.dlqAnalysis?.totalDeadLetters, 0);
    assert.match(report.summary, /healthy/i);
  });

  it('AC-5: triageIncident flags unreachable cluster as CRITICAL', async () => {
    const client = new EventoreClient('http://localhost:8080/api/v1');
    (client as any).inspectCluster = async () => {
      throw new Error('Connection refused: 9092');
    };

    const report = await client.triageIncident('conn-failing-1');
    assert.equal(report.verdict, 'CRITICAL');
    assert.equal(report.cluster.reachable, false);
    assert.match(report.remediationActions[0], /unreachable/i);
  });

  it('AC-6: triageIncident flags consumer lag skew and DLQ poison pills', async () => {
    const client = new EventoreClient('http://localhost:8080/api/v1');
    (client as any).inspectCluster = async () => ({ clusterId: 'kafka-prod' });
    (client as any).inspectConsumerGroups = async () => [{ groupId: 'order-processors' }];
    (client as any).inspectLag = async () => [
        { topic: 'orders', partition: 0, lag: 50 },
        { topic: 'orders', partition: 1, lag: 12500 }, // High skew
      ];
    (client as any).listDlqTopics = async () => [
      { dlqTopic: 'orders.DLQ', partitionCount: 1 },
    ];
    (client as any).inspectDlqMessages = async (_connection: string, topic: string) => {
      assert.equal(topic, 'orders.DLQ');
      return [
      {
        exceptionClass: 'com.fasterxml.jackson.databind.exc.InvalidFormatException',
        failureReason: 'Cannot deserialize Instant from String "invalid-timestamp"',
        stackTraceSnippet: 'at com.fasterxml.jackson.databind.Deserializer.deserialize(Deserializer.java:124)',
      },
    ]; };

    const report = await client.triageIncident('conn-skewed', { destination: 'orders' });
    assert.equal(report.verdict, 'CRITICAL');
    assert.equal(report.lagAnalysis?.skewDetected, true);
    assert.equal(report.lagAnalysis?.maxLag, 12500);
    assert.equal(report.dlqAnalysis?.totalDeadLetters, 1);
    assert.equal(report.dlqAnalysis?.topExceptions[0].exceptionClass, 'com.fasterxml.jackson.databind.exc.InvalidFormatException');
    assert.ok(report.remediationActions.some(r => r.includes('High consumer lag') || r.includes('poisoned messages')));
  });

  it('AC-7: README documents incident triage tooling and prompt', () => {
    assert.match(readmeSource, /eventore_triage_incident/);
    assert.match(readmeSource, /eventore_incident_triage/);
  });

  it('reports UNKNOWN when requested checks fail', async () => {
    const client = new EventoreClient('http://localhost:8080/api/v1');
    client.inspectCluster = async () => ({});
    client.inspectConsumerGroups = async () => { throw new Error('unsupported'); };
    client.listDlqTopics = async () => { throw new Error('broker unavailable'); };
    const report = await client.triageIncident('connection');
    assert.equal(report.verdict, 'UNKNOWN');
    assert.equal(report.incompleteChecks.length, 2);
    assert.doesNotMatch(report.summary, /healthy/);
  });
});
