import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

describe('EPIC-2 dashboard diagnostics contract', () => {
  it('AC-1: Dashboard polls diagnostics subscriptions every 10s', () => {
    const page = readFileSync(join(root, 'src/pages/DashboardPage.tsx'), 'utf8');
    expect(page).toMatch(/diagnosticsSubscriptions/);
    expect(page).toMatch(/refetchInterval:\s*10_000/);
  });

  it('AC-2: subscription table columns present', () => {
    const page = readFileSync(join(root, 'src/pages/DashboardPage.tsx'), 'utf8');
    expect(page).toMatch(/Connection/);
    expect(page).toMatch(/Transport/);
    expect(page).toMatch(/Last error/);
  });

  it('AC-4: empty state copy', () => {
    const page = readFileSync(join(root, 'src/pages/DashboardPage.tsx'), 'utf8');
    expect(page).toMatch(/No active subscriptions/);
  });
});
