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
      fs.writeFileSync(path.join(root, 'index.html'), '<p class="edition">EVENTORE 9.9.9 / STALE RELEASE</p>');
      if (version) fs.writeFileSync(path.join(root, 'product/release.json'), JSON.stringify({
        version, publishedAt: '2026-10-04T16:10:42Z', url: `https://example.org/v${version}`,
      }));
      execFileSync(process.execPath, [path.join(root, 'scripts/build-product.mjs')]);
      const html = fs.readFileSync(path.join(root, 'releases.html'), 'utf8');
      assert.match(html, /Introduced in 0\.2\.1/);
      assert.match(html, version === '0.3.0' ? /Unreleased changes/ : /Unreleased \u00b7 0\.3\.0/);
      assert.doesNotMatch(html, /Introduced in 0\.3\.0/);
      const unreleased = html.slice(html.indexOf('id="unreleased"'), html.indexOf('id="released"'));
      const released = html.slice(html.indexOf('id="released"'));
      assert.match(unreleased, /id="october-audit"/);
      assert.doesNotMatch(unreleased, /Introduced in/);
      assert.doesNotMatch(released, /id="enterprise-ha-triage"/);
      for (const file of ['product.html', 'releases.html', 'roadmap.html']) {
        const page = fs.readFileSync(path.join(root, file), 'utf8');
        assert.match(page, /Source changes on main are not included in the published release/);
        if (version) assert.ok(page.includes(`EventOre ${version}`));
        else {
          assert.match(page, /No verified release is available/);
          assert.doesNotMatch(page, /helm install/);
        }
        if (version === '0.2.1') assert.doesNotMatch(page, /--version 0\.3\.0/);
      }
      const homepage = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
      assert.doesNotMatch(homepage, /9\.9\.9/);
      if (!version) assert.match(homepage, /DEVELOPMENT STATUS/);
    } finally {
      fs.rmSync(root, {recursive: true, force: true});
    }
  });
}
