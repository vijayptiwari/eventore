import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'backend', 'openapi');
const streams = ['core', 'inspect', 'diagnostics', 'kafka', 'kinesis'];

const commonPath = join(root, 'common', 'schemas.yaml');
const commonDoc = yaml.load(readFileSync(commonPath, 'utf8'));

const bundled = {
  openapi: '3.0.3',
  info: {
    title: 'Eventore API (bundled)',
    version: '0.1.0',
    description: 'Auto-bundled OpenAPI 3.0 contract combining all active streams'
  },
  servers: [{ url: '/api/v1' }],
  paths: {},
  components: {
    parameters: { ...(commonDoc.components?.parameters || {}) },
    schemas: { ...(commonDoc.components?.schemas || {}) }
  }
};

for (const stream of streams) {
  const filePath = join(root, 'streams', `${stream}-api.yaml`);
  if (!existsSync(filePath)) continue;
  let raw = readFileSync(filePath, 'utf8');
  // Re-route relative common schemas reference to internal components section
  raw = raw.replace(/\.\.\/common\/schemas\.yaml#\/components\//g, '#/components/');
  const doc = yaml.load(raw);
  if (doc?.paths) {
    Object.assign(bundled.paths, doc.paths);
  }
  if (doc?.components?.schemas) {
    Object.assign(bundled.components.schemas, doc.components.schemas);
  }
  if (doc?.components?.parameters) {
    Object.assign(bundled.components.parameters, doc.components.parameters);
  }
}

const out = join(root, 'eventore-api-bundled.yaml');
writeFileSync(out, yaml.dump(bundled, { noRefs: true, lineWidth: -1 }));
console.log(`Wrote ${out} (${Object.keys(bundled.paths).length} paths, ${Object.keys(bundled.components.schemas).length} schemas)`);
