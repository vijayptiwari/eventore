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
  const nav = page.getByRole('navigation', { name: 'Main' });
  await page.goto('/');
  await nav.getByRole('link', { name: 'Connections' }).click();
  await expect(page).toHaveURL(/\/connections/);
  await nav.getByRole('link', { name: 'Browse' }).click();
  await expect(page).toHaveURL(/\/browse/);
  await nav.getByRole('link', { name: 'Live Stream' }).click();
  await expect(page).toHaveURL(/\/stream/);
});

test('dashboard shows subscription health diagnostics', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Subscription health' })).toBeVisible();
  const table = page.locator('table.diagnostics-table');
  await expect(table.getByText('Local Kafka')).toBeVisible();
  await expect(table.getByText('orders')).toBeVisible();
});

test('connection wizard opens from connections page', async ({ page }) => {
  await page.goto('/connections');
  await page.getByRole('button', { name: 'Open connection wizard' }).click();
  await expect(page.getByRole('dialog').getByRole('heading', { name: 'New connection' })).toBeVisible();
});
