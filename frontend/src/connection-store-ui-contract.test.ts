import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const srcRoot = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(srcRoot, '..', '..');

describe('REQ-101 Connection Store UI Contract', () => {
  it('AC-1: ConnectionsPage contains storage banner and live migration controls', () => {
    const page = readFileSync(join(srcRoot, 'pages', 'ConnectionsPage.tsx'), 'utf8');
    expect(page).toMatch(/connection-store-banner/);
    expect(page).toMatch(/connection-store-badge/);
    expect(page).toMatch(/💾 Storage:/);
    expect(page).toMatch(/Optimistic Locking Enabled/);
    expect(page).toMatch(/api\.getConnectionStoreInfo/);
    expect(page).toMatch(/api\.migrateConnectionStore/);
    expect(page).toMatch(/Live store migration/);
    expect(page).toMatch(/Migration target store/);
  });

  it('AC-2: types.ts defines ConnectionStoreType and ConnectionStoreInfo with all 4 supported stores', () => {
    const types = readFileSync(join(srcRoot, 'api', 'types.ts'), 'utf8');
    expect(types).toMatch(/export type ConnectionStoreType = 'FILE' \| 'JDBC' \| 'K8S_CRD' \| 'IN_MEMORY'/);
    expect(types).toMatch(/export interface ConnectionStoreInfo/);
    expect(types).toMatch(/supportsOptimisticLocking:\s*boolean/);
    expect(types).toMatch(/profileCount:\s*number/);
    expect(types).toMatch(/availableStores:\s*ConnectionStoreType\[\]/);
  });

  it('AC-3: client.ts exposes getConnectionStoreInfo and migrateConnectionStore', () => {
    const client = readFileSync(join(srcRoot, 'api', 'client.ts'), 'utf8');
    expect(client).toMatch(/getConnectionStoreInfo:\s*\(\)\s*=>/);
    expect(client).toMatch(/\/connections\/store/);
    expect(client).toMatch(/migrateConnectionStore:\s*\(targetType:\s*ConnectionStoreType\)\s*=>/);
    expect(client).toMatch(/\/connections\/store\/migrate\?targetType=/);
  });

  it('AC-4: index.css defines dedicated styling for connection store banner and badges', () => {
    const css = readFileSync(join(srcRoot, 'index.css'), 'utf8');
    expect(css).toMatch(/\.connection-store-banner/);
    expect(css).toMatch(/\.connection-store-badge/);
    expect(css).toMatch(/\.connection-store-desc/);
    expect(css).toMatch(/\.connection-store-actions/);
  });

  it('AC-5: OpenAPI bundled schema includes /connections/store and /connections/store/migrate', () => {
    const openapi = readFileSync(join(repoRoot, 'backend', 'openapi', 'eventore-api-bundled.yaml'), 'utf8');
    expect(openapi).toMatch(/\/connections\/store:/);
    expect(openapi).toMatch(/\/connections\/store\/migrate:/);
    expect(openapi).toMatch(/ConnectionStoreType/);
    expect(openapi).toMatch(/ConnectionStoreInfo/);
  });
});
