import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const srcRoot = join(dirname(fileURLToPath(import.meta.url)));

describe('FEAT-5 onboarding wizard contract', () => {
  it('AC-1: Connections page opens ConnectionWizardDialog', () => {
    const page = readFileSync(join(srcRoot, 'pages', 'ConnectionsPage.tsx'), 'utf8');
    expect(page).toMatch(/ConnectionWizardDialog/);
    expect(page).toMatch(/setWizardOpen\(true\)/);
  });

  it('AC-2: Dashboard getting-started links to wizard query param', () => {
    const dash = readFileSync(join(srcRoot, 'pages', 'DashboardPage.tsx'), 'utf8');
    expect(dash).toMatch(/\/connections\?wizard=1/);
  });

  it('AC-3: wizard has 4 steps and Back/Next navigation', () => {
    const wizard = readFileSync(join(srcRoot, 'components', 'ConnectionWizardDialog.tsx'), 'utf8');
    expect(wizard).toMatch(/Preset/);
    expect(wizard).toMatch(/Credentials/);
    expect(wizard).toMatch(/Validate/);
    expect(wizard).toMatch(/Done/);
    expect(wizard).toMatch(/goBack/);
    expect(wizard).toMatch(/goNext/);
  });

  it('AC-4: cancel confirms discard and cleans up draft', () => {
    const wizard = readFileSync(join(srcRoot, 'components', 'ConnectionWizardDialog.tsx'), 'utf8');
    expect(wizard).toMatch(/confirm/);
    expect(wizard).toMatch(/cleanupDraft/);
    expect(wizard).toMatch(/deleteConnection/);
  });

  it('AC-5: READONLY hides wizard when cannot manage', () => {
    const wizard = readFileSync(join(srcRoot, 'components', 'ConnectionWizardDialog.tsx'), 'utf8');
    expect(wizard).toMatch(/canManage/);
    expect(wizard).toMatch(/read-only deployment/i);
  });

  it('FEAT-5.2: secret ref helper and protocol guide links', () => {
    const wizard = readFileSync(join(srcRoot, 'components', 'ConnectionWizardDialog.tsx'), 'utf8');
    expect(wizard).toMatch(/env:VAR_NAME/);
    expect(wizard).toMatch(/protocolGuideUrl/);
    const shared = readFileSync(join(srcRoot, 'connections', 'connectionFormShared.ts'), 'utf8');
    expect(shared).toMatch(/PROTOCOL_EXTRA_FIELDS/);
    expect(wizard).toMatch(/listPlatforms/);
  });

  it('FEAT-5.3: validate-before-save with draft cleanup on failure', () => {
    const wizard = readFileSync(join(srcRoot, 'components', 'ConnectionWizardDialog.tsx'), 'utf8');
    expect(wizard).toMatch(/validateConnection/);
    expect(wizard).toMatch(/createConnection/);
    expect(wizard).toMatch(/Validate connection/);
  });

  it('FEAT-5.4: browse handoff and optional test publish', () => {
    const wizard = readFileSync(join(srcRoot, 'components', 'ConnectionWizardDialog.tsx'), 'utf8');
    expect(wizard).toMatch(/Open in Browse/);
    expect(wizard).toMatch(/\/browse\?connectionId=/);
    expect(wizard).toMatch(/Send test message/);
    expect(wizard).toMatch(/invalidateQueries.*connections/);
  });
});
