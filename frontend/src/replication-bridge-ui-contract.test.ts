import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const srcRoot = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(srcRoot, '..', '..');

describe('REQ-110 Cross-Broker Bridging UI Contract', () => {
  it('AC-1: BridgesPage and BridgeWizardDialog contain KPIs, route flow, lifecycle, and rule tester', () => {
    const page = readFileSync(join(srcRoot, 'pages', 'BridgesPage.tsx'), 'utf8');
    expect(page).toMatch(/Cross-Broker Replication Engine/);
    expect(page).toMatch(/bridge-kpi-grid/);
    expect(page).toMatch(/Total Pipelines/);
    expect(page).toMatch(/Active Replicating/);
    expect(page).toMatch(/Replicated Messages/);
    expect(page).toMatch(/bridge-route-flow/);
    expect(page).toMatch(/bridge-flow-arrow/);
    expect(page).toMatch(/api\.listBridges/);
    expect(page).toMatch(/api\.startBridge/);
    expect(page).toMatch(/api\.stopBridge/);
    expect(page).toMatch(/api\.deleteBridge/);
    expect(page).toMatch(/api\.testBridge/);
    expect(page).toMatch(/bridge-tester-box/);

    const dialog = readFileSync(join(srcRoot, 'components', 'BridgeWizardDialog.tsx'), 'utf8');
    expect(dialog).toMatch(/Create Replication Bridge/);
    expect(dialog).toMatch(/Source Broker \(Ingress\)/);
    expect(dialog).toMatch(/Target Broker \(Egress\)/);
    expect(dialog).toMatch(/Automated Loop Prevention/);
    expect(dialog).toMatch(/Payload Regex Filter/);
    expect(dialog).toMatch(/Header Transformations & Metadata Injection/);
    expect(dialog).toMatch(/api\.createBridge/);
  });

  it('AC-2: types.ts defines all replication bridge domain types and DTOs', () => {
    const types = readFileSync(join(srcRoot, 'api', 'types.ts'), 'utf8');
    expect(types).toMatch(/export type ReplicationBridgeState = 'RUNNING' \| 'STOPPED' \| 'ERROR' \| 'PAUSED'/);
    expect(types).toMatch(/export interface ReplicationBridgeStats/);
    expect(types).toMatch(/export interface ReplicationBridge/);
    expect(types).toMatch(/export interface ReplicationBridgeRequest/);
    expect(types).toMatch(/export interface ReplicationTestRequest/);
    expect(types).toMatch(/export interface ReplicationTestResult/);
  });

  it('AC-3: client.ts exposes full CRUD and lifecycle API methods for bridges', () => {
    const client = readFileSync(join(srcRoot, 'api', 'client.ts'), 'utf8');
    expect(client).toMatch(/listBridges:\s*\(\)\s*=>/);
    expect(client).toMatch(/createBridge:\s*\(body:\s*import\('\.\/types'\)\.ReplicationBridgeRequest\)\s*=>/);
    expect(client).toMatch(/getBridge:\s*\(bridgeId:\s*string\)\s*=>/);
    expect(client).toMatch(/updateBridge:\s*\(bridgeId:\s*string,\s*body:\s*import\('\.\/types'\)\.ReplicationBridgeRequest\)\s*=>/);
    expect(client).toMatch(/deleteBridge:\s*\(bridgeId:\s*string\)\s*=>/);
    expect(client).toMatch(/startBridge:\s*\(bridgeId:\s*string\)\s*=>/);
    expect(client).toMatch(/stopBridge:\s*\(bridgeId:\s*string\)\s*=>/);
    expect(client).toMatch(/testBridge:\s*\(body:\s*import\('\.\/types'\)\.ReplicationTestRequest\)\s*=>/);
  });

  it('AC-4: App.tsx and AppLayout.tsx wire up /bridges route and navigation link', () => {
    const app = readFileSync(join(srcRoot, 'App.tsx'), 'utf8');
    expect(app).toMatch(/<Route path="\/bridges" element={<BridgesPage \/>} \/>/);

    const layout = readFileSync(join(srcRoot, 'components', 'AppLayout.tsx'), 'utf8');
    expect(layout).toMatch(/<NavLink to="\/bridges">Bridges<\/NavLink>/);
  });

  it('AC-5: index.css defines dedicated styling for bridge cards, flow arrows, and throughput metrics', () => {
    const css = readFileSync(join(srcRoot, 'index.css'), 'utf8');
    expect(css).toMatch(/\.bridges-page/);
    expect(css).toMatch(/\.bridge-kpi-grid/);
    expect(css).toMatch(/\.bridge-card/);
    expect(css).toMatch(/\.bridge-state-badge/);
    expect(css).toMatch(/\.bridge-route-flow/);
    expect(css).toMatch(/\.bridge-flow-arrow/);
    expect(css).toMatch(/\.bridge-metrics-grid/);
    expect(css).toMatch(/\.bridge-tester-box/);
  });

  it('AC-6: OpenAPI bundled spec and generated schema contain all bridge endpoints and schemas', () => {
    const openapi = readFileSync(join(repoRoot, 'backend', 'openapi', 'eventore-api-bundled.yaml'), 'utf8');
    expect(openapi).toMatch(/\/bridges:/);
    expect(openapi).toMatch(/\/bridges\/\{bridgeId\}:/);
    expect(openapi).toMatch(/\/bridges\/\{bridgeId\}\/start:/);
    expect(openapi).toMatch(/\/bridges\/\{bridgeId\}\/stop:/);
    expect(openapi).toMatch(/\/bridges\/test:/);
    expect(openapi).toMatch(/ReplicationBridge:/);
    expect(openapi).toMatch(/ReplicationBridgeRequest:/);
    expect(openapi).toMatch(/ReplicationBridgeStats:/);
    expect(openapi).toMatch(/ReplicationBridgeState:/);

    const schema = readFileSync(join(srcRoot, 'api', 'generated', 'schema.ts'), 'utf8');
    expect(schema).toMatch(/"\/bridges":/);
    expect(schema).toMatch(/"\/bridges\/\{bridgeId\}\/start":/);
    expect(schema).toMatch(/ReplicationBridge:/);
    expect(schema).toMatch(/ReplicationBridgeRequest:/);
  });
});
