#!/usr/bin/env node
// Render every published provider combination to catch image-tag drift and collisions.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
const version = fs.readFileSync('VERSION', 'utf8').trim();
const matrix = JSON.parse(fs.readFileSync('deploy/ci-backend-images.json', 'utf8'));
const protocols = { kafka: 'KAFKA', mqtt: 'MQTT', jms: 'JMS', pulsar: 'PULSAR', rabbitmq: 'RABBITMQ', kinesis: 'KINESIS', 'gcp-pubsub': 'GCP_PUBSUB', 'azure-servicebus': 'AZURE_SERVICE_BUS' };
const tags = new Set();
for (const item of matrix) {
  const providers = item.tag === 'all' ? Object.values(protocols) : item.tag === 'kafka-kinesis' ? ['KAFKA', 'KINESIS'] : [protocols[item.tag]];
  assert.ok(providers.every(Boolean));
  const rendered = execFileSync('helm', ['template', 'eventore', 'deploy/helm/eventore', '--set-json', `eventore.streamProviders=${JSON.stringify(providers)}`], { encoding: 'utf8' });
  const image = `ghcr.io/vijayptiwari/eventore-backend:${version}-${item.tag}`;
  assert.ok(rendered.includes(`image: "${image}"`), item.tag);
  assert.ok(rendered.includes(`image: "ghcr.io/vijayptiwari/eventore-frontend:${version}"`));
  assert.ok(!tags.has(image), 'Provider variants must never share an image tag');
  tags.add(image);
}
const mcp = execFileSync('helm', ['template', 'eventore-mcp', 'deploy/helm/eventore-mcp'], { encoding: 'utf8' });
assert.ok(mcp.includes(`image: "ghcr.io/vijayptiwari/eventore-mcp:${version}"`));
console.log(`Verified ${matrix.length} distinct backend tags and version-pinned frontend/MCP charts.`);
