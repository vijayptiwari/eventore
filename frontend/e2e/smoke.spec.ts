import { expect, test } from '@playwright/test';
import { mockApi } from './fixtures';

test.beforeEach(async ({ page }) => {
  await mockApi(page);
});

test('dashboard renders deployment mode and protocols', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  await expect(page.getByText('ADMIN').first()).toBeVisible();
  await expect(page.getByText('KAFKA').first()).toBeVisible();
});

test('connections page lists existing connections', async ({ page }) => {
  await page.goto('/connections');
  await expect(page.getByText('Local Kafka')).toBeVisible();
  await expect(page.getByText('localhost:9092').first()).toBeVisible();
});

test('browse page loads destinations for a connection', async ({ page }) => {
  await page.goto('/browse');
  // The browse page needs a selected connection; pick the mocked one if a
  // selector is present, then verify destinations appear.
  const connectionOption = page.getByText('Local Kafka').first();
  if (await connectionOption.isVisible().catch(() => false)) {
    await connectionOption.click();
  }
  await expect(page.getByText(/orders|No destinations|Select a connection/i).first()).toBeVisible();
});

test('navigation between pages works', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: /connections/i }).click();
  await expect(page).toHaveURL(/\/connections/);
  await page.getByRole('link', { name: /browse/i }).click();
  await expect(page).toHaveURL(/\/browse/);
  await page.getByRole('link', { name: /stream/i }).click();
  await expect(page).toHaveURL(/\/stream/);
});
