import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const srcRoot = join(dirname(fileURLToPath(import.meta.url)));

describe('FEAT-1.3 UI contract', () => {
  it('AC-1: AppLayout exposes Settings control and auth error banner', () => {
    const layout = readFileSync(join(srcRoot, 'components', 'AppLayout.tsx'), 'utf8');
    expect(layout).toMatch(/Settings/);
    expect(layout).toMatch(/ApiTokenSettingsDialog/);
    expect(layout).toMatch(/isApiAuthError/);
    expect(layout).toMatch(/API authentication failed/);
  });

  it('AC-6: auth error banner includes 401 status per EPICS', () => {
    const layout = readFileSync(join(srcRoot, 'components', 'AppLayout.tsx'), 'utf8');
    expect(layout).toMatch(/API authentication failed \(401\)/);
  });

  it('AC-1: ApiTokenSettingsDialog has password input, Save, and Clear', () => {
    const dialog = readFileSync(join(srcRoot, 'components', 'ApiTokenSettingsDialog.tsx'), 'utf8');
    expect(dialog).toMatch(/type="password"/);
    expect(dialog).toMatch(/saveApiToken/);
    expect(dialog).toMatch(/clearApiToken/);
    expect(dialog).toMatch(/Save/);
    expect(dialog).toMatch(/Clear/);
  });

  it('AC-3: StreamWorkspaceContext appends encoded token query param to wsUrl', () => {
    const wsContext = readFileSync(join(srcRoot, 'stream', 'StreamWorkspaceContext.tsx'), 'utf8');
    expect(wsContext).toMatch(/token=\$\{encodeURIComponent\(apiToken\)\}/);
    expect(wsContext).toMatch(/wsUrl\.includes\('\?'\)/);
  });
});
