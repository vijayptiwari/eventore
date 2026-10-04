#!/usr/bin/env node
// VERSION is the release source of truth. --check fails CI on manifest drift.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const version = fs.readFileSync(path.join(root, 'VERSION'), 'utf8').trim();
if (!/^\d+\.\d+\.\d+$/.test(version)) throw new Error('VERSION must be a stable semantic version');
const check = process.argv.includes('--check');
const drift = [];
function update(file, transform) {
  const target = path.join(root, file);
  const before = fs.readFileSync(target, 'utf8');
  const after = transform(before);
  if (before !== after) {
    if (check) drift.push(file);
    else fs.writeFileSync(target, after);
  }
}
for (const name of fs.readdirSync(path.join(root, 'backend'))) {
  if (!fs.existsSync(path.join(root, 'backend', name, 'pom.xml'))) continue;
  update(`backend/${name}/pom.xml`, s => s.replace(/(<parent>[\s\S]*?<groupId>com\.eventore<\/groupId>[\s\S]*?<version>)[^<]+/, '$1' + version));
}
update('backend/pom.xml', s => s.replace(/(<artifactId>eventore-parent<\/artifactId>\s*<version>)[^<]+/, '$1' + version).replace(/(<eventore.version>)[^<]+/, '$1' + version));
for (const dir of ['frontend', 'mcp/eventore-mcp']) {
  for (const name of ['package.json', 'package-lock.json']) {
    update(`${dir}/${name}`, s => {
      const data = JSON.parse(s);
      if (data.version === version && (!data.packages || data.packages[''].version === version)) return s;
      data.version = version;
      if (data.packages) data.packages[''].version = version;
      return JSON.stringify(data, null, 2) + '\n';
    });
  }
}
for (const chart of ['eventore', 'eventore-mcp']) {
  update(`deploy/helm/${chart}/Chart.yaml`, s => s.replace(/^version:.*$/m, `version: ${version}`).replace(/^appVersion:.*$/m, `appVersion: "${version}"`));
}
for (const dir of ['backend/openapi', 'backend/openapi/streams', 'backend/openapi/common', 'backend/eventore-server/src/main/resources/openapi']) {
  for (const name of fs.readdirSync(path.join(root, dir))) {
    if (!name.endsWith('.yaml') || name.includes('bundled')) continue;
    update(`${dir}/${name}`, s => s.replace(/^(  version:) .+$/m, '$1 ' + version));
  }
}
update('backend/eventore-server/src/main/java/com/eventore/config/OpenApiConfig.java', s => s.replace(/\.version\("[^"]+"\)/, `.version("${version}")`));
update('mcp/eventore-mcp/src/server.ts', s => s.replace(/version: '[^']+'/, `version: '${version}'`));
update('docs/product/updates.json', s => {
  const data = JSON.parse(s);
  if (data.manifestVersion === version) return s;
  data.manifestVersion = version;
  return JSON.stringify(data, null, 2) + '\n';
});
if (process.env.GITHUB_REF_TYPE === 'tag' && process.env.GITHUB_REF_NAME !== `v${version}`) drift.push('Git tag does not match VERSION');
if (drift.length) throw new Error('Version drift: ' + drift.join(', '));
console.log(`${check ? 'Verified' : 'Synchronized'} product version ${version}`);
