/**
 * Phase 9 / Plan 09-05 (TEST-01) — Admin orders (2 tests).
 * D-12: admin list orders + navigate to detail.
 *
 * Selectors confirmed từ sources/frontend/src/app/admin/orders/page.tsx:
 * - Heading: "Quản lý đơn hàng" (h1)
 * - Table: thead có cột Mã đơn, Khách hàng, Tổng tiền, Trạng thái, Ngày đặt, Thao tác
 * - Row action: button aria-label="Xem chi tiết đơn hàng" → router.push(/admin/orders/{id})
 * - Detail page: confirmed /admin/orders/[id] route (từ router.push)
 *
 * NOTE: Admin orders page KHÔNG có form update status inline — chỉ có navigate to detail.
 * Test ADM-ORD-2 verify detail page render (không phải update state inline).
 *
 * Dùng admin storageState từ global-setup.ts (D-13).
 */
import { test, expect } from '@playwright/test';

test.use({ storageState: 'e2e/storageState/admin.json' });

test('ADM-ORD-1: list /admin/orders render heading + table', async ({ page }) => {
  await page.goto('/admin/orders');
  // Confirmed heading từ page.tsx: <h1>Quản lý đơn hàng</h1>
  await expect(page.getByRole('heading', { name: 'Quản lý đơn hàng' })).toBeVisible({ timeout: 10000 });
  // Table render (dù empty hay có data)
  await expect(page.locator('table')).toBeVisible({ timeout: 10000 });
  // Table header columns confirmed từ page.tsx
  await expect(page.getByRole('columnheader', { name: 'Mã đơn' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Trạng thái' })).toBeVisible();
});

test('ADM-ORD-2: click "Xem chi tiết" trên row đầu tiên → navigate /admin/orders/:id', async ({ page }) => {
  await page.goto('/admin/orders');
  await expect(page.getByRole('heading', { name: 'Quản lý đơn hàng' })).toBeVisible({ timeout: 10000 });

  // Đợi loading xong
  await page.waitForTimeout(2000);

  // Kiểm tra có row data không
  const firstDataRow = page.locator('table tbody tr').filter({ hasNotText: '' }).first();
  const rowCount = await page.locator('table tbody tr').count();

  if (rowCount === 0) {
    test.skip(true, 'Không có đơn hàng trong DB — cần đặt hàng trước');
    return;
  }

  // Check có phải loading skeleton không (skeleton row không có button)
  const detailBtn = page.locator('[aria-label="Xem chi tiết đơn hàng"]').first();
  const btnVisible = await detailBtn.isVisible({ timeout: 5000 }).catch(() => false);

  if (!btnVisible) {
    test.skip(true, 'Chưa có đơn hàng thực — bảng đang ở trạng thái empty hoặc skeleton');
    return;
  }

  await detailBtn.click();
  // Navigate tới /admin/orders/:id
  await page.waitForURL(/\/admin\/orders\/[^/]+$/, { timeout: 10000 });
  expect(page.url()).toMatch(/\/admin\/orders\/.+/);
});

test('ADM-ORD-3: detail page render items + KHÔNG còn placeholder Phase 8', async ({ page }) => {
  await page.goto('/admin/orders');
  await expect(page.getByRole('heading', { name: 'Quản lý đơn hàng' })).toBeVisible({ timeout: 10000 });
  await page.waitForTimeout(2000);

  const detailBtn = page.locator('[aria-label="Xem chi tiết đơn hàng"]').first();
  const btnVisible = await detailBtn.isVisible({ timeout: 5000 }).catch(() => false);
  if (!btnVisible) {
    test.skip(true, 'Chưa có đơn hàng — cần seed trước');
    return;
  }
  await detailBtn.click();
  await page.waitForURL(/\/admin\/orders\/[^/]+$/, { timeout: 10000 });

  // ADMIN-06 SC1: placeholder cũ KHÔNG còn xuất hiện
  await expect(page.getByText('khả dụng sau khi Phase 8')).toHaveCount(0);

  // ADMIN-06 SC2: shipping + items section render
  await expect(page.getByText('Thông tin giao hàng')).toBeVisible({ timeout: 5000 });
  await expect(page.getByRole('heading', { name: 'Sản phẩm' })).toBeVisible();
});
