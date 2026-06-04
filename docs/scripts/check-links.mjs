#!/usr/bin/env node
/**
 * Validates internal href/src in docs/ (relative file targets).
 * Run from repo root: node docs/scripts/check-links.mjs
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const docsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const exts = new Set(['.html', '.md', '.js', '.json', '.css', '.mjs']);
const skipHref = /^(https?:|mailto:|tel:|#|javascript:)/i;

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name);
    const st = fs.statSync(p);
    if (st.isDirectory()) walk(p, out);
    else if (exts.has(path.extname(name))) out.push(p);
  }
  return out;
}

function extractLinks(content) {
  const links = [];
  const re = /(?:href|src)=["']([^"']+)["']/gi;
  let m;
  while ((m = re.exec(content))) links.push(m[1]);
  return links;
}

const files = walk(docsDir);
const issues = [];

for (const file of files) {
  const relFile = path.relative(docsDir, file).replace(/\\/g, '/');
  const content = fs.readFileSync(file, 'utf8');
  for (const raw of extractLinks(content)) {
    if (skipHref.test(raw)) continue;
    if (raw === '/' || raw === '') continue;
    if (raw.startsWith('//')) continue;
    const [target, hash] = raw.split('#');
    if (!target || target === '.') continue;

    let resolved;
    if (target.startsWith('/')) {
      resolved = path.join(docsDir, target.replace(/^\//, ''));
    } else {
      resolved = path.normalize(path.join(path.dirname(file), target));
    }

    const exists = fs.existsSync(resolved);
    const isDir = exists && fs.statSync(resolved).isDirectory();
    if (!exists || isDir) {
      issues.push({ file: relFile, link: raw, resolved: path.relative(docsDir, resolved).replace(/\\/g, '/') });
    }
  }
}

if (issues.length) {
  console.error('Broken internal links:\n');
  for (const i of issues) {
    console.error(`  ${i.file}: ${i.link} -> ${i.resolved}`);
  }
  process.exit(1);
}
console.log(`OK: ${files.length} files, no broken relative href/src targets.`);
