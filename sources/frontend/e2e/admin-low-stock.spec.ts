/**
 * Phase 19 / Plan 19-04 — Low-stock section smoke test.
 * Verify: section title + list rows OR empty placeholder + click row navigation.
 *
 * Reuse admin storageState từ global-setup.ts.
 */
import { test, expect } from '@playwright/test';

test.use({ storageState: 'e2e/storageState/admin.json' });

test('ADM-LOWSTOCK-1: section render với items HOẶC empty placeholder', async ({ page }) => {
  await page.goto('/admin');

  // Section title visible (bên trong ChartCard)
  await expect(page.getByText('Sản phẩm sắp hết hàng')).toBeVisible({ timeout: 10000 });

  // Wait cho fetchLowStock settle
  await page.waitForTimeout(3000);

  // Accept either: list rows OR empty state (depends on seed data)
  const rowCount = await page.locator('li').filter({ hasText: 'Còn ' }).count();
  const hasEmpty = await page
    .getByText('Tất cả sản phẩm đủ hàng ✓')
    .isVisible()
    .catch(() => false);
  expect(rowCount > 0 || hasEmpty).toBeTruthy();
});

test('ADM-LOWSTOCK-2: click row navigate /admin/products?highlight=...', async ({ page }) => {
  await page.goto('/admin');
  await expect(page.getByText('Sản phẩm sắp hết hàng')).toBeVisible({ timeout: 10000 });
  await page.waitForTimeout(3000);

  const firstRow = page.locator('li').filter({ hasText: 'Còn ' }).first();
  const rowExists = (await firstRow.count()) > 0;

  if (!rowExists) {
    // Empty seed state — skip nav test
    test.skip(true, 'Không có sản phẩm low-stock trong DB hiện tại — skip navigation test');
  }

  await firstRow.click();
  await page.waitForURL(/\/admin\/products\?highlight=/, { timeout: 5000 });
  expect(page.url()).toContain('/admin/products?highlight=');
});
