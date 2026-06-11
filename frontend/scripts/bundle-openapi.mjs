import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'backend', 'openapi');
const streams = ['core', 'inspect', 'diagnostics', 'kafka', 'kinesis'];
const common = readFileSync(join(root, 'common', 'schemas.yaml'), 'utf8');

function extractPaths(yaml) {
  const paths = {};
  const lines = yaml.split('\n');
  let inPaths = false;
  let currentPath = null;
  for (const line of lines) {
    if (line === 'paths:') {
      inPaths = true;
      continue;
    }
    if (inPaths) {
      if (line.startsWith('components:') || line.startsWith('x-')) {
        break;
      }
      const pathMatch = line.match(/^  (\/[^:]+):/);
      if (pathMatch) {
        currentPath = pathMatch[1];
        paths[currentPath] = [];
      }
      if (currentPath && line.trim()) {
        paths[currentPath].push(line);
      }
    }
  }
  return paths;
}

let pathsBlock = 'paths:\n';
for (const stream of streams) {
  const file = join(root, 'streams', `${stream}-api.yaml`);
  if (!existsSync(file)) continue;
  const yaml = readFileSync(file, 'utf8');
  const paths = extractPaths(yaml);
  for (const [path, body] of Object.entries(paths)) {
    pathsBlock += `  ${path}:\n`;
    pathsBlock += body.slice(1).join('\n') + '\n';
  }
}

const commonComponents = common.split('components:')[1] ?? '';
const bundled = `openapi: 3.0.3
info:
  title: Eventore API (bundled)
  version: 0.1.0
servers:
  - url: /api/v1
${pathsBlock}components:${commonComponents}`;

const out = join(root, 'eventore-api-bundled.yaml');
writeFileSync(out, bundled);
console.log('Wrote', out);
