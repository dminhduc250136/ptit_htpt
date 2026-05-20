/**
 * MODULE 04 — THANH TOÁN (Checkout).
 *
 * Phủ: route guard, address picker, nhập coupon, chọn phương thức thanh toán, đặt hàng.
 * Dùng user storageState.
 *
 * Selectors (verified — checkout/page.tsx):
 *   - Heading h1 "Thanh toán"
 *   - AddressPicker: trigger button "Địa chỉ đã lưu" → listbox → [role=option]
 *   - Coupon: placeholder "Nhập mã giảm giá" + button "Áp dụng"
 *   - Payment: radio "Thanh toán khi nhận hàng" (COD) / "Chuyển khoản ngân hàng" / "Ví điện tử"
 *   - Submit: button type=submit "Đặt hàng"
 *
 * Strategy A: skip nếu không thêm được SP vào giỏ / user không có địa chỉ đã lưu.
 */
import { test, expect } from './utils/fixtures';
import { USER_STATE, ensureCartHasItem } from './utils/helpers';

test.use({ storageState: USER_STATE });

/** Đảm bảo giỏ có item rồi mở /checkout. Trả false nếu giỏ trống và không thêm được SP. */
async function gotoCheckoutWithItem(page: import('@playwright/test').Page): Promise<boolean> {
  // Kiểm tra giỏ hiện tại (seed-dev V103 tạo sẵn item) — nếu rỗng thì thêm SP.
  await page.goto('/cart');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(1200);
  const hasItem = await page
    .getByRole('button', { name: /Xóa sản phẩm/ })
    .first()
    .isVisible({ timeout: 5000 })
    .catch(() => false);
  if (!hasItem && !(await ensureCartHasItem(page))) return false;

  await page.goto('/checkout');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(1000);
  return true;
}

test.describe('04-CHECKOUT: Thanh toán', () => {
  // ───────────────────────────────────────────────────────────────
  test('CHK-01: Trang checkout render đủ section (giao hàng, thanh toán, đơn hàng)', async ({
    page,
  }) => {
    if (!(await gotoCheckoutWithItem(page))) {
      test.skip(true, 'Không thêm được SP vào giỏ — Strategy A skip');
      return;
    }

    await expect(page.getByRole('heading', { level: 1, name: /Thanh toán/ })).toBeVisible({
      timeout: 10000,
    });
    await expect(
      page.getByRole('heading', { level: 3, name: /Thông tin giao hàng/ })
    ).toBeVisible();
    await expect(
      page.getByRole('heading', { level: 3, name: /Phương thức thanh toán/ })
    ).toBeVisible();
    await expect(page.getByRole('heading', { level: 3, name: /Đơn hàng của bạn/ })).toBeVisible();
  });

  // ───────────────────────────────────────────────────────────────
  test('CHK-02: Chọn địa chỉ từ AddressPicker (nếu có địa chỉ đã lưu)', async ({ page }) => {
    if (!(await gotoCheckoutWithItem(page))) {
      test.skip(true, 'Không thêm được SP vào giỏ — Strategy A skip');
      return;
    }

    const trigger = page.getByRole('button', { name: /Địa chỉ đã lưu/ }).first();
    if (!(await trigger.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, 'AddressPicker không hiển thị — user chưa có địa chỉ đã lưu (Strategy A)');
      return;
    }

    await trigger.click();
    await page.waitForTimeout(400);
    const option = page.locator('[role="option"]').first();
    if (!(await option.isVisible({ timeout: 3000 }).catch(() => false))) {
      test.skip(true, 'User chưa có địa chỉ đã lưu — seed qua /profile/addresses (Strategy A)');
      return;
    }

    await option.click();
    // Sau khi chọn → form được điền (họ tên không rỗng)
    await page.waitForTimeout(500);
    const fullName = page.getByLabel(/Họ và tên/);
    await expect(fullName).not.toHaveValue('');
  });

  // ───────────────────────────────────────────────────────────────
  test('CHK-03: Áp dụng coupon không hợp lệ → báo lỗi', async ({ page }) => {
    if (!(await gotoCheckoutWithItem(page))) {
      test.skip(true, 'Không thêm được SP vào giỏ — Strategy A skip');
      return;
    }

    const couponInput = page.getByPlaceholder(/Nhập mã giảm giá/);
    if (!(await couponInput.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, 'Ô coupon không hiển thị — Strategy A skip');
      return;
    }

    await couponInput.fill('MAGIAMGIAKHONGTONTAI999');
    await page.getByRole('button', { name: /Áp dụng/ }).click();
    await page.waitForTimeout(2000);

    // Có thông báo lỗi coupon không hợp lệ / không tồn tại
    await expect(
      page.getByText(/không hợp lệ|không tồn tại|hết hạn|không tìm thấy|không áp dụng/i).first()
    ).toBeVisible({ timeout: 5000 });
  });

  // ───────────────────────────────────────────────────────────────
  test('CHK-04: Chọn phương thức thanh toán COD', async ({ page }) => {
    if (!(await gotoCheckoutWithItem(page))) {
      test.skip(true, 'Không thêm được SP vào giỏ — Strategy A skip');
      return;
    }

    const cod = page.getByLabel(/Thanh toán khi nhận hàng/);
    await expect(cod).toBeVisible({ timeout: 10000 });
    await cod.check();
    await expect(cod).toBeChecked();
  });

  // ───────────────────────────────────────────────────────────────
  test('CHK-05: Đặt hàng thành công → redirect tới đơn hàng', async ({ page }) => {
    if (!(await gotoCheckoutWithItem(page))) {
      test.skip(true, 'Không thêm được SP vào giỏ — Strategy A skip');
      return;
    }

    // Chọn địa chỉ đã lưu nếu có; nếu không, điền thủ công
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
      // Điền form thủ công
      await page.getByLabel(/Họ và tên/).fill('E2E Người Nhận');
      await page.getByLabel(/Số điện thoại/).fill('0901234567');
      await page.getByLabel(/Địa chỉ/).first().fill('123 Đường E2E');
      await page.getByLabel(/Phường\/Xã/).fill('Phường 1');
      await page.getByLabel(/Quận\/Huyện/).fill('Quận 1');
      await page.getByLabel(/Tỉnh\/Thành phố/).fill('TP. Hồ Chí Minh');
    }

    // Chọn COD
    const cod = page.getByLabel(/Thanh toán khi nhận hàng/);
    if (await cod.isVisible({ timeout: 3000 }).catch(() => false)) {
      await cod.check();
    }

    await page.getByRole('button', { name: /Đặt hàng/ }).click();

    // Thành công: redirect tới /orders|/profile, hoặc toast thành công
    await Promise.race([
      page.waitForURL(/\/orders|\/profile/, { timeout: 20000, waitUntil: 'domcontentloaded' }),
      page
        .getByText(/đặt hàng thành công|đã đặt hàng|thành công/i)
        .first()
        .waitFor({ timeout: 20000 }),
    ]);
  });

  // ───────────────────────────────────────────────────────────────
  test('CHK-06: Submit form thiếu thông tin giao hàng → báo lỗi validation', async ({ page }) => {
    if (!(await gotoCheckoutWithItem(page))) {
      test.skip(true, 'Không thêm được SP vào giỏ — Strategy A skip');
      return;
    }

    // KHÔNG điền địa chỉ → submit ngay
    await page.getByRole('button', { name: /Đặt hàng/ }).click();
    await page.waitForTimeout(1500);

    // Vẫn ở /checkout (không tạo được order) — validation chặn
    expect(page.url()).toContain('/checkout');
  });
});
