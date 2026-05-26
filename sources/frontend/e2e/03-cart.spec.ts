/**
 * MODULE 03 — GIỎ HÀNG (Cart).
 *
 * Phủ: thêm SP vào giỏ, xem giỏ, tăng/giảm số lượng, xóa item, tóm tắt đơn hàng, empty state.
 * Dùng user storageState (giỏ hàng gắn với user đã đăng nhập).
 *
 * Tiền đề: seed-dev V103 tạo sẵn 2 cart_items cho user demo. Mỗi test gọi ensureCartHasItem()
 * để bảo đảm giỏ không rỗng (thêm SP nếu cần) trước khi thao tác.
 *
 * Selectors (verified — cart/page.tsx):
 *   - Heading h1 "Giỏ hàng"; empty: h2 "Giỏ hàng trống"
 *   - Nút xóa item: aria-label="Xóa sản phẩm"
 *   - Tăng/giảm: button text "+" / "−"; số lượng: .qtyValue
 *   - Tổng tiền: .totalPrice; nút checkout: link "Tiến hành thanh toán" → /checkout
 */
import { test, expect } from './utils/fixtures';
import { USER_STATE, ensureCartHasItem } from './utils/helpers';

test.use({ storageState: USER_STATE });

/** Vào /cart và đảm bảo giỏ có ≥1 item. Trả false nếu không có SP nào mua được. */
async function openCartWithItem(page: import('@playwright/test').Page): Promise<boolean> {
  await page.goto('/cart');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(1200);

  const hasItem = await page
    .getByRole('button', { name: /Xóa sản phẩm/ })
    .first()
    .isVisible({ timeout: 5000 })
    .catch(() => false);
  if (hasItem) return true;

  // Giỏ rỗng → thêm 1 SP rồi quay lại /cart
  if (!(await ensureCartHasItem(page))) return false;
  await page.goto('/cart');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(1200);
  return page
    .getByRole('button', { name: /Xóa sản phẩm/ })
    .first()
    .isVisible({ timeout: 5000 })
    .catch(() => false);
}

test.describe('03-CART: Giỏ hàng', () => {
  // ───────────────────────────────────────────────────────────────
  test('CART-01: Giỏ hàng có item → hiển thị danh sách', async ({ page }) => {
    if (!(await openCartWithItem(page))) {
      test.skip(true, 'Không có SP nào mua được — Strategy A skip');
      return;
    }
    await expect(page.getByRole('heading', { level: 1, name: /Giỏ hàng/ })).toBeVisible({
      timeout: 10000,
    });
    await expect(page.getByRole('button', { name: /Xóa sản phẩm/ }).first()).toBeVisible();
  });

  // ───────────────────────────────────────────────────────────────
  test('CART-02: Tăng số lượng item → qty cập nhật', async ({ page }) => {
    if (!(await openCartWithItem(page))) {
      test.skip(true, 'Giỏ hàng không có item — Strategy A skip');
      return;
    }
    // qty value: span đứng ngay sau nút "−" (CSS module → class hashed, dùng quan hệ DOM)
    const qty = page.getByRole('button', { name: '−', exact: true }).first().locator('xpath=following-sibling::span[1]');
    await expect(qty).toBeVisible({ timeout: 10000 });
    const before = Number((await qty.textContent())?.trim() ?? '0');

    await page.getByRole('button', { name: '+', exact: true }).first().click();
    await page.waitForTimeout(1800);

    const after = Number((await qty.textContent())?.trim() ?? '0');
    expect(after).toBe(before + 1);
  });

  // ───────────────────────────────────────────────────────────────
  test('CART-03: Giảm số lượng item', async ({ page }) => {
    if (!(await openCartWithItem(page))) {
      test.skip(true, 'Giỏ hàng không có item — Strategy A skip');
      return;
    }
    const qty = page.getByRole('button', { name: '−', exact: true }).first().locator('xpath=following-sibling::span[1]');
    await expect(qty).toBeVisible({ timeout: 10000 });
    // Tăng lên trước để chắc chắn qty ≥ 2
    await page.getByRole('button', { name: '+', exact: true }).first().click();
    await page.waitForTimeout(1800);
    const before = Number((await qty.textContent())?.trim() ?? '0');

    await page.getByRole('button', { name: '−', exact: true }).first().click();
    await page.waitForTimeout(1800);
    const after = Number((await qty.textContent())?.trim() ?? '0');
    expect(after).toBe(before - 1);
  });

  // ───────────────────────────────────────────────────────────────
  test('CART-04: Xóa item khỏi giỏ', async ({ page }) => {
    if (!(await openCartWithItem(page))) {
      test.skip(true, 'Giỏ hàng không có item — Strategy A skip');
      return;
    }
    const removeBtns = page.getByRole('button', { name: /Xóa sản phẩm/ });
    const countBefore = await removeBtns.count();

    await removeBtns.first().click();
    await page.waitForTimeout(1800);

    const countAfter = await page.getByRole('button', { name: /Xóa sản phẩm/ }).count();
    const isEmpty = await page
      .getByRole('heading', { level: 2, name: /Giỏ hàng trống/ })
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    expect(countAfter === countBefore - 1 || isEmpty).toBe(true);
  });

  // ───────────────────────────────────────────────────────────────
  test('CART-05: Tóm tắt đơn hàng hiển thị tạm tính + tổng tiền', async ({ page }) => {
    if (!(await openCartWithItem(page))) {
      test.skip(true, 'Giỏ hàng không có item — Strategy A skip');
      return;
    }
    await expect(
      page.getByRole('heading', { level: 3, name: /Tóm tắt đơn hàng/ })
    ).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/Tạm tính/)).toBeVisible();
    // Dòng "Tổng cộng" + giá tiền (CSS module hashed → dùng text ngữ nghĩa)
    await expect(page.getByText('Tổng cộng')).toBeVisible();
  });

  // ───────────────────────────────────────────────────────────────
  test('CART-06: Nút "Tiến hành thanh toán" dẫn tới /checkout', async ({ page }) => {
    if (!(await openCartWithItem(page))) {
      test.skip(true, 'Giỏ hàng không có item — Strategy A skip');
      return;
    }
    const checkoutLink = page.getByRole('link', { name: /Tiến hành thanh toán/ });
    await expect(checkoutLink).toBeVisible({ timeout: 10000 });
    await checkoutLink.click();
    await page.waitForURL(/\/checkout/, { timeout: 10000, waitUntil: 'domcontentloaded' });
  });

  // ───────────────────────────────────────────────────────────────
  test('CART-07: Xóa hết item → hiển thị empty state', async ({ page }) => {
    if (!(await openCartWithItem(page))) {
      test.skip(true, 'Giỏ hàng không có item — Strategy A skip');
      return;
    }
    // Xóa lần lượt tới khi hết
    for (let i = 0; i < 20; i++) {
      const removeBtn = page.getByRole('button', { name: /Xóa sản phẩm/ }).first();
      if (!(await removeBtn.isVisible({ timeout: 2000 }).catch(() => false))) break;
      await removeBtn.click();
      await page.waitForTimeout(1500);
    }
    await expect(
      page.getByRole('heading', { level: 2, name: /Giỏ hàng trống/ })
    ).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('link', { name: /Tiếp tục mua sắm/ })).toBeVisible();

    // Khôi phục: thêm lại 1 item để các test sau (cùng user) vẫn có giỏ không rỗng
    await ensureCartHasItem(page);
  });
});
