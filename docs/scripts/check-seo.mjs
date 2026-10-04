#!/usr/bin/env node
// Validate the published metadata contract without external dependencies.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const { pages } = JSON.parse(read('seo/pages.json'));
const site = JSON.parse(read('seo/site.json'));
const origin = site.origin.replace(/\/$/, '') + site.basePath;
const escape = s => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
const titles = new Set();
for (const page of pages) {
  const html = read(page.file);
  assert.equal((html.match(/<h1\b/g) || []).length, 1, `${page.file}: one primary heading`);
  assert.equal((html.match(/<title>/g) || []).length, 1, `${page.file}: one title`);
  assert.ok(html.includes(`<title>${escape(page.title)}</title>`), `${page.file}: title drift`);
  assert.ok(!titles.has(page.title), `${page.file}: duplicate title`);
  titles.add(page.title);
  assert.equal((html.match(/name="description"/g) || []).length, 1, `${page.file}: description count`);
  assert.equal((html.match(/rel="canonical"/g) || []).length, 1, `${page.file}: canonical count`);
  const canonical = origin + (page.path === 'index.html' ? '' : page.path);
  assert.equal(html.match(/rel="canonical"\s+href="([^"]+)"/)?.[1], canonical, `${page.file}: canonical mismatch`);
  assert.ok(read('sitemap.xml').includes(`<loc>${canonical}</loc>`), `${page.file}: missing from sitemap`);
  assert.ok(html.includes('aria-label="Primary"'), `${page.file}: missing static navigation`);
  assert.ok(html.includes('href="#main"') && html.includes('id="main"'), `${page.file}: broken skip link`);
  for (const match of html.matchAll(/<script type="application\/ld\+json">(.*?)<\/script>/gs)) {
    assert.equal(JSON.parse(match[1])['@context'], 'https://schema.org');
  }
}
const png = fs.readFileSync(path.join(root, site.defaultOgImage));
assert.equal(png.subarray(1, 4).toString(), 'PNG', 'Social preview must be a PNG');
assert.equal(png.readUInt32BE(16), 1200);
assert.equal(png.readUInt32BE(20), 630);
for (const name of ['logo.svg', 'logo-light.svg', 'logo-mark.svg']) {
  assert.equal(read(`assets/${name}`), fs.readFileSync(path.join(root, '../frontend/public/assets', name), 'utf8'), `${name}: product/site mismatch`);
}
console.log(`OK: ${pages.length} pages; titles, canonicals, sitemap, structured data, navigation, social preview, and shared logos.`);
