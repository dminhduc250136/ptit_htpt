/**
 * Phase 19 / Plan 19-04 — Admin charts smoke test.
 * Verify: KPI row vẫn render + dropdown 4 options + chart titles + SVG render + range change refetch.
 *
 * Reuse admin storageState từ global-setup.ts (D-13 Phase 9).
 */
import { test, expect } from '@playwright/test';

test.use({ storageState: 'e2e/storageState/admin.json' });

test('ADM-CHARTS-1: dashboard render KPI + dropdown + 4 chart cards + SVG', async ({ page }) => {
  await page.goto('/admin');

  // KPI row vẫn intact (existing Phase 9 KPI cards)
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible({ timeout: 10000 });
  await expect(page.locator('[data-card-label="Sản phẩm"]')).toBeVisible();
  await expect(page.locator('[data-card-label="Tổng đơn hàng"]')).toBeVisible();

  // Time-window dropdown visible với 4 options + default 30d
  const dropdown = page.locator('#time-window');
  await expect(dropdown).toBeVisible();
  await expect(dropdown).toHaveValue('30d');
  await expect(dropdown.locator('option')).toHaveCount(4);

  // 4 chart titles render
  await expect(page.getByText('Doanh thu')).toBeVisible();
  await expect(page.getByText('Sản phẩm bán chạy')).toBeVisible();
  await expect(page.getByText('Phân phối trạng thái')).toBeVisible();
  await expect(page.getByText('Khách hàng đăng ký')).toBeVisible();

  // Wait cho ít nhất 1 chart SVG render (recharts emits <svg>)
  // BE endpoints có thể empty → component render <p> empty state thay vì SVG; accept either.
  await page.waitForTimeout(3000);
  const svgCount = await page.locator('svg.recharts-surface').count();
  const emptyMsgCount = await page.getByText('Chưa có dữ liệu trong khoảng này').count();
  const noOrders = await page.getByText('Chưa có đơn hàng nào').count();
  const noProducts = await page.getByText('Chưa có sản phẩm bán ra trong khoảng này').count();
  // Tổng cộng ≥1 chart phải có dấu hiệu render (svg HOẶC empty placeholder)
  expect(svgCount + emptyMsgCount + noOrders + noProducts).toBeGreaterThan(0);
});

test('ADM-CHARTS-2: đổi dropdown range → 3 charts refetch không crash', async ({ page }) => {
  await page.goto('/admin');
  await expect(page.locator('#time-window')).toBeVisible({ timeout: 10000 });

  await page.locator('#time-window').selectOption('7d');
  await expect(page.locator('#time-window')).toHaveValue('7d');

  // Wait cho refetch settle
  await page.waitForTimeout(2500);

  // Charts still present (titles persist; not crashed)
  await expect(page.getByText('Doanh thu')).toBeVisible();
  await expect(page.getByText('Sản phẩm bán chạy')).toBeVisible();
  await expect(page.getByText('Khách hàng đăng ký')).toBeVisible();

  // Đổi sang 'all' để verify dropdown vẫn responsive
  await page.locator('#time-window').selectOption('all');
  await expect(page.locator('#time-window')).toHaveValue('all');
});
