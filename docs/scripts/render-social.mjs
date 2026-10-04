#!/usr/bin/env node
// Run after npm ci in frontend; uses the project's existing Playwright dependency.
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const require = createRequire(path.join(root, 'frontend/package.json'));
const { chromium } = require('playwright');
const browser = await chromium.launch({ headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 1 });
  const svg = fs.readFileSync(path.join(root, 'docs/assets/og-card.svg'), 'utf8');
  await page.setContent('<style>html,body{margin:0;width:1200px;height:630px;overflow:hidden}svg{display:block}</style>' + svg);
  await page.screenshot({ path: path.join(root, 'docs/assets/og-card.png') });
  console.log('Rendered docs/assets/og-card.png (1200×630).');
} finally {
  await browser.close();
}
