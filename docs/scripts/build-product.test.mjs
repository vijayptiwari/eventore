import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

for (const version of [null, '0.2.1', '0.3.0']) {
  test(`milestone attribution with published version ${version}`, () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'eventore-history-'));
    try {
      fs.mkdirSync(path.join(root, 'scripts'));
      fs.mkdirSync(path.join(root, 'product'));
      fs.copyFileSync(new URL('./build-product.mjs', import.meta.url), path.join(root, 'scripts/build-product.mjs'));
      fs.copyFileSync(new URL('../product/updates.json', import.meta.url), path.join(root, 'product/updates.json'));
      fs.writeFileSync(path.join(root, 'index.html'), '<p class="edition">Development</p>');
      if (version) fs.writeFileSync(path.join(root, 'product/release.json'), JSON.stringify({
        version, publishedAt: '2026-10-04T16:10:42Z', url: `https://example.org/v${version}`,
      }));
      execFileSync(process.execPath, [path.join(root, 'scripts/build-product.mjs')]);
      const html = fs.readFileSync(path.join(root, 'releases.html'), 'utf8');
      assert.match(html, /Introduced in 0\.2\.1/);
      assert.match(html, /Unreleased · 0\.3\.0/);
      assert.doesNotMatch(html, /Introduced in 0\.3\.0/);
      if (version) assert.match(html, new RegExp(`Latest published release: ${version.replaceAll('.', '\\.')}`));
      else assert.match(html, /No verified release is available/);
    } finally {
      fs.rmSync(root, {recursive: true, force: true});
    }
  });
}
