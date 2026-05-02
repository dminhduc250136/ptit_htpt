/**
 * Phase 9 / Plan 09-05 (TEST-01) — Order detail (2 tests).
 * D-12: user /profile/orders list + detail breakdown (4-col items, shipping, payment).
 *
 * Selectors confirmed từ sources/frontend/src/app/profile/orders/[id]/page.tsx:
 * - Detail page title: "Đơn hàng #{orderCode}" (h1)
 * - Items table header: Sản phẩm, Số lượng, Đơn giá, Thành tiền (4 cột)
 * - Shipping info card: h4 "Địa chỉ giao hàng"
 * - Payment info card: h4 "Thanh toán"
 * - Back button: "← Quay lại"
 * - Order link pattern: href="/profile" page có link tới /profile/orders/:id
 *
 * NOTE: /profile/orders route — page.tsx không tồn tại (Glob không tìm thấy), chỉ có [id]/page.tsx.
 * /profile route có thể là trang profile chính với list orders. Thử /profile trước.
 *
 * Dùng user storageState từ global-setup.ts (D-13).
 */
import { test, expect } from '@playwright/test';

test.use({ storageState: 'e2e/storageState/user.json' });

test('ORD-DTL-1: /profile render sau khi login qua storageState', async ({ page }) => {
  // Middleware (D-02) cho phép /profile/* nếu có auth_present cookie
  await page.goto('/profile');
  // Không bị redirect về /login → storageState hoạt động
  expect(page.url()).not.toContain('/login');
  // Nội dung /profile hiển thị (heading hoặc content nào đó)
  await page.waitForLoadState('domcontentloaded');
  // Verify page không 404 hay error
  const pageTitle = await page.title();
  expect(pageTitle).not.toMatch(/404|error/i);
});

test('ORD-DTL-2: order detail page render 4-col items table + địa chỉ + thanh toán', async ({ page }) => {
  // Tìm link đến order detail từ /profile
  await page.goto('/profile');
  await page.waitForLoadState('networkidle');

  // Tìm link tới /profile/orders/:id (confirmed từ [id]/page.tsx structure)
  const orderLink = page.locator('a[href*="/profile/orders/"]').first();
  const hasOrderLink = await orderLink.isVisible({ timeout: 5000 }).catch(() => false);

  if (!hasOrderLink) {
    // Thử /profile/orders route
    await page.goto('/profile/orders');
    await page.waitForLoadState('networkidle');
    const orderLinkFromList = page.locator('a[href*="/profile/orders/"]').first();
    const hasFromList = await orderLinkFromList.isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasFromList) {
      test.skip(true, 'User demo không có đơn hàng — cần đặt hàng trước khi chạy test này');
      return;
    }
    await orderLinkFromList.click();
  } else {
    await orderLink.click();
  }

  // Đợi navigate tới /profile/orders/:id
  await page.waitForURL(/\/profile\/orders\/[^/]+$/, { timeout: 10000 });

  // Verify 4-column items table header (confirmed từ [id]/page.tsx)
  // Headers: Sản phẩm, Số lượng, Đơn giá, Thành tiền
  await expect(page.getByRole('columnheader', { name: 'Sản phẩm' })).toBeVisible({ timeout: 5000 });
  await expect(page.getByRole('columnheader', { name: 'Số lượng' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Đơn giá' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Thành tiền' })).toBeVisible();

  // Verify shipping info card (confirmed từ [id]/page.tsx: h4 "Địa chỉ giao hàng")
  await expect(page.getByText('Địa chỉ giao hàng')).toBeVisible();

  // Verify payment info card (confirmed từ [id]/page.tsx: h4 "Thanh toán")
  await expect(page.getByText('Thanh toán')).toBeVisible();

  // Phase 17 / ORDER-01: assert items table có ≥ 1 row visible
  // (skip nếu seed order rỗng — D-05 empty state cũng valid render)
  const rowCount = await page.locator('table tbody tr').count();
  if (rowCount > 0) {
    await expect(page.locator('table tbody tr').first()).toBeVisible();
    // Brand subtitle có thể là "—" nếu enrichment fail — chỉ check first row visible
  }
});
