#!/usr/bin/env node
// Only a published GitHub Release advances the website's adoption status.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repository = process.env.GITHUB_REPOSITORY || 'vijayptiwari/eventore';
const headers = { Accept: 'application/vnd.github+json' };
if (process.env.GH_TOKEN) headers.Authorization = `Bearer ${process.env.GH_TOKEN}`;
const response = await fetch(`https://api.github.com/repos/${repository}/releases/latest`, { headers });
if (response.status === 404) {
  fs.rmSync(path.join(root, 'product/release.json'), { force: true });
  console.log('No published release; cleared cached release metadata.');
  process.exit(0);
}
if (!response.ok) throw new Error(`GitHub release lookup failed: ${response.status}`);
const release = await response.json();
if (release.draft || release.prerelease || !/^v\d+\.\d+\.\d+$/.test(release.tag_name)) throw new Error('Not a stable versioned release');
const version = release.tag_name.slice(1);
for (const name of [`eventore-${version}.tgz`, `eventore-mcp-${version}.tgz`, 'SHA256SUMS']) {
  if (!release.assets.some(a => a.name === name && a.state === 'uploaded')) throw new Error(`Release asset missing: ${name}`);
}
fs.writeFileSync(path.join(root, 'product/release.json'), JSON.stringify({ version, publishedAt: release.published_at, url: release.html_url, tag: release.tag_name }, null, 2) + '\n');
console.log(`Website release verified: ${release.tag_name}`);
