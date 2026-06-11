import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, it } from 'node:test';

const packageRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const protocolToolsSource = readFileSync(join(packageRoot, 'src', 'protocol-tools.ts'), 'utf8');
const clientSource = readFileSync(join(packageRoot, 'src', 'eventore-client.ts'), 'utf8');
const resourcesSource = readFileSync(join(packageRoot, 'src', 'resources.ts'), 'utf8');
const serverSource = readFileSync(join(packageRoot, 'src', 'server.ts'), 'utf8');
const promptsSource = readFileSync(join(packageRoot, 'src', 'prompts.ts'), 'utf8');

describe('FEAT-11.2 protocol-specific MCP tools', () => {
  it('registers RabbitMQ, GCP, and Azure dedicated tools', () => {
    assert.match(protocolToolsSource, /'eventore_rabbitmq_list_queues'/);
    assert.match(protocolToolsSource, /'eventore_rabbitmq_queue_detail'/);
    assert.match(protocolToolsSource, /'eventore_rabbitmq_queue_depth'/);
    assert.match(protocolToolsSource, /'eventore_gcp_list_topics'/);
    assert.match(protocolToolsSource, /'eventore_gcp_list_subscriptions'/);
    assert.match(protocolToolsSource, /'eventore_gcp_subscription_backlog'/);
    assert.match(protocolToolsSource, /'eventore_azure_list_entities'/);
    assert.match(protocolToolsSource, /'eventore_azure_list_subscriptions'/);
    assert.match(protocolToolsSource, /'eventore_azure_peek_messages'/);
    assert.match(protocolToolsSource, /'eventore_azure_entity_backlog'/);
  });

  it('wires protocol tools from server bootstrap', () => {
    assert.match(serverSource, /registerProtocolTools/);
  });
});

describe('FEAT-11.3 capability matrix resource', () => {
  it('exposes eventore://capability-matrix', () => {
    assert.match(resourcesSource, /eventore:\/\/capability-matrix/);
    assert.match(resourcesSource, /INSPECTOR_CAPABILITY_MATRIX/);
  });
});

describe('FEAT-11.4 diagnostics MCP tool', () => {
  it('calls diagnostics subscriptions REST path', () => {
    assert.match(protocolToolsSource, /'eventore_diagnostics_subscriptions'/);
    assert.match(clientSource, /diagnosticsSubscriptions\(\)/);
    assert.match(clientSource, /\/diagnostics\/subscriptions/);
  });
});

describe('FEAT-11.5 protocol inspect prompts', () => {
  it('defines RabbitMQ, GCP, and Azure inspection playbooks', () => {
    assert.match(promptsSource, /'eventore_rabbitmq_inspection'/);
    assert.match(promptsSource, /'eventore_gcp_pubsub_inspection'/);
    assert.match(promptsSource, /'eventore_azure_servicebus_inspection'/);
  });
});
