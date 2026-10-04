import { expect, test } from '@playwright/test';

/**
 * REQ-61: Live Backend Playwright E2E Test Suite.
 *
 * This test suite runs against an unmocked live Spring Boot backend
 * (and optional live Kafka broker).
 *
 * It is conditionally gated on live backend availability:
 * - In local dev without backend running: skips gracefully.
 * - In CI or with live backend: executes full end-to-end unmocked flows.
 */

test.beforeEach(async ({ request }) => {
  let backendAvailable = false;
  try {
    const configRes = await request.get('http://localhost:8080/api/v1/config', {
      timeout: 3000,
    });
    if (configRes.ok()) {
      const data = await configRes.json();
      backendAvailable = Boolean(data.deploymentMode);
    }
  } catch {
    backendAvailable = false;
  }

  if (process.env.EVENTORE_REQUIRE_LIVE_BACKEND === 'true') {
    expect(backendAvailable, 'Release CI requires a healthy live backend').toBe(true);
  }
  test.skip(
    !backendAvailable,
    'Live backend not available at http://localhost:8080 (REQ-61 requires Docker / live backend)',
  );
});

test.describe('Live Backend E2E (REQ-61)', () => {
  test('unmocked dashboard loads real backend configuration', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

    // The live backend defaults to DEV deployment mode
    await expect(page.getByText('DEV').first()).toBeVisible();

    // Check that supported protocols are populated from backend control plane
    await expect(page.getByText('KAFKA').first()).toBeVisible();
  });

  test('unmocked connection lifecycle: create, validate, list, and delete', async ({
    page,
    request,
  }) => {
    const testConnName = `live-e2e-${Date.now()}`;

    // 1. Direct API creation to test real backend storage
    const createRes = await request.post('http://localhost:8080/api/v1/connections', {
      data: {
        name: testConnName,
        protocol: 'KAFKA',
        brokerUrl: 'localhost:9092',
      },
    });
    expect([200, 201]).toContain(createRes.status());
    const created = await createRes.json();
    expect(created.id).toBeDefined();

    // 2. Validate endpoint execution against live connector
    const validateRes = await request.post(
      `http://localhost:8080/api/v1/connections/${created.id}/validate`,
    );
    if (process.env.EVENTORE_REQUIRE_LIVE_BACKEND === 'true') {
      expect(validateRes.status()).toBe(200);
    } else {
      expect([200, 422, 502]).toContain(validateRes.status());
    }

    // 3. UI loads the saved connection from real backend
    await page.goto('/connections');
    await expect(page.getByRole('heading', { name: 'Saved connections' })).toBeVisible();
    await expect(page.getByText(testConnName)).toBeVisible();

    // 4. Test button on the row triggers real backend validation
    const row = page.locator('tr', { hasText: testConnName });
    await row.getByRole('button', { name: 'Test' }).click();
    await expect(row.locator('.tag-ok, .stream-error')).toBeVisible({ timeout: 15_000 });

    // 5. Cleanup: delete connection via API
    const deleteRes = await request.delete(
      `http://localhost:8080/api/v1/connections/${created.id}`,
    );
    expect(deleteRes.status()).toBe(204);

    // Refresh and verify removal
    await page.reload();
    await expect(page.getByText(testConnName)).not.toBeVisible();
  });
});
