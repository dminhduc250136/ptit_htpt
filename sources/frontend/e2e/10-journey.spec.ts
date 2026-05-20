/**
 * MODULE 10 — HÀNH TRÌNH ĐẦU-CUỐI (End-to-End Journey).
 *
 * Khác với các spec module (test từng tính năng riêng lẻ), spec này nối nhiều bước thành
 * một luồng nghiệp vụ hoàn chỉnh — verify các phần ráp vào nhau đúng.
 *
 * JOURNEY-1 (khách hàng): duyệt SP → thêm giỏ → xem giỏ → checkout → đặt hàng → xem đơn.
 * JOURNEY-2 (admin):       đăng nhập admin → dashboard → quản lý sản phẩm → quản lý đơn.
 *
 * Strategy A: skip-if-no-data ở các điểm phụ thuộc seed.
 */
import { test, expect } from './utils/fixtures';
import { USER_STATE, ADMIN_STATE, ensureCartHasItem } from './utils/helpers';

// ─────────────────────────────────────────────────────────────────
test.describe('10-JOURNEY-1: Hành trình mua hàng (khách hàng)', () => {
  test.use({ storageState: USER_STATE });

  test('JOURNEY-1: Duyệt → giỏ hàng → checkout → đặt hàng → đơn hàng', async ({ page }) => {
    // ── Bước 1+2: Duyệt sản phẩm + thêm vào giỏ ──
    // ensureCartHasItem duyệt catalog, bỏ qua SP không mua được, thêm 1 SP vào giỏ.
    if (!(await ensureCartHasItem(page))) {
      test.skip(true, 'Không thêm được SP vào giỏ — Strategy A skip');
      return;
    }

    // ── Bước 3: Xem giỏ hàng ──
    await page.goto('/cart');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByRole('heading', { level: 1, name: /Giỏ hàng/ })).toBeVisible({
      timeout: 10000,
    });
    await expect(page.getByRole('button', { name: /Xóa sản phẩm/ }).first()).toBeVisible({
      timeout: 10000,
    });

    // ── Bước 4: Tiến hành checkout ──
    await page.getByRole('link', { name: /Tiến hành thanh toán/ }).click();
    await page.waitForURL(/\/checkout/, { timeout: 10000, waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('domcontentloaded');

    // ── Bước 5: Điền thông tin giao hàng + chọn thanh toán ──
    const trigger = page.getByRole('button', { name: /Địa chỉ đã lưu/ }).first();
    let addressReady = false;
    if (await trigger.isVisible({ timeout: 3000 }).catch(() => false)) {
      await trigger.click();
      await page.waitForTimeout(400);
      const option = page.locator('[role="option"]').first();
      if (await option.isVisible({ timeout: 3000 }).catch(() => false)) {
        await option.click();
        addressReady = true;
      }
    }
    if (!addressReady) {
      await page.getByLabel(/Họ và tên/).fill('E2E Journey');
      await page.getByLabel(/Số điện thoại/).fill('0901234567');
      await page.getByLabel(/Địa chỉ/).first().fill('789 Đường Journey');
      await page.getByLabel(/Phường\/Xã/).fill('Phường 3');
      await page.getByLabel(/Quận\/Huyện/).fill('Quận 3');
      await page.getByLabel(/Tỉnh\/Thành phố/).fill('TP. Hồ Chí Minh');
    }
    const cod = page.getByLabel(/Thanh toán khi nhận hàng/);
    if (await cod.isVisible({ timeout: 3000 }).catch(() => false)) {
      await cod.check();
    }

    // ── Bước 6: Đặt hàng ──
    await page.getByRole('button', { name: /Đặt hàng/ }).click();
    await Promise.race([
      page.waitForURL(/\/orders|\/profile/, { timeout: 20000, waitUntil: 'domcontentloaded' }),
      page
        .getByText(/đặt hàng thành công|đã đặt hàng|thành công/i)
        .first()
        .waitFor({ timeout: 20000 }),
    ]);

    // ── Bước 7: Xác minh đơn hàng xuất hiện trong lịch sử ──
    await page.goto('/profile/orders');
    await page.waitForLoadState('domcontentloaded');
    await expect(
      page.getByRole('heading', { level: 1, name: /Lịch sử đơn hàng/ })
    ).toBeVisible({ timeout: 10000 });
    await expect(page.locator('a[href*="/profile/orders/"]').first()).toBeVisible({
      timeout: 10000,
    });
  });
});

// ─────────────────────────────────────────────────────────────────
test.describe('10-JOURNEY-2: Hành trình quản trị (admin)', () => {
  test.use({ storageState: ADMIN_STATE });

  test('JOURNEY-2: Dashboard → quản lý sản phẩm → quản lý đơn hàng', async ({ page }) => {
    // ── Bước 1: Dashboard ──
    await page.goto('/admin');
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible({ timeout: 10000 });

    // ── Bước 2: Quản lý sản phẩm — tạo SP mới ──
    await page.goto('/admin/products');
    await expect(page.getByRole('heading', { name: 'Quản lý sản phẩm' })).toBeVisible({
      timeout: 10000,
    });
    await page.getByRole('button', { name: '+ Thêm sản phẩm' }).click();
    await expect(page.getByRole('heading', { name: 'Thêm sản phẩm mới' })).toBeVisible({
      timeout: 5000,
    });
    await page.getByLabel('Tên sản phẩm').fill(`Journey SP ${Date.now()}`);
    await page.getByLabel('Giá bán').fill('299000');
    await page.getByLabel('Tồn kho').fill('25');
    await page.waitForTimeout(1500);
    const categorySelect = page.locator('select').first();
    if ((await categorySelect.locator('option').count()) > 1) {
      await categorySelect.selectOption({ index: 1 });
      await page.getByRole('button', { name: 'Thêm sản phẩm', exact: true }).click();
      await expect(page.getByText('Sản phẩm đã được thêm thành công')).toBeVisible({
        timeout: 10000,
      });
    }

    // ── Bước 3: Quản lý đơn hàng ──
    await page.goto('/admin/orders');
    await expect(page.getByRole('heading', { name: 'Quản lý đơn hàng' })).toBeVisible({
      timeout: 10000,
    });
    await expect(page.locator('table')).toBeVisible({ timeout: 10000 });
  });
});
