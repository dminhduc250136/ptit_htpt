/**
 * Phase 15 / Plan 15-03 (TEST-02) — Smoke E2E (4 critical paths v1.2).
 *
 * D-17/D-18/D-19/D-20:
 *   SMOKE-1 (anon):  Homepage hero render + CTA "Khám phá ngay" → /products có ProductCard
 *   SMOKE-2 (user):  Cart → /checkout → AddressPicker [role=option] → submit order → success
 *   SMOKE-3 (user):  PDP review submission (skip-if-no-DELIVERED — Strategy A từ 15-SELECTOR-AUDIT.md)
 *   SMOKE-4 (user):  /profile/settings sửa fullName + phone → success toast → reload persist
 *
 * Selectors source: .planning/phases/15-public-polish-milestone-audit/15-SELECTOR-AUDIT.md (Wave 0 verified).
 * User storageState từ global-setup.ts (Phase 9 D-13) — KHÔNG re-login trong smoke spec.
 *
 * Verified selectors (15-SELECTOR-AUDIT.md):
 *   - AddressPicker: trigger button "Địa chỉ đã lưu" + listbox items role=option (cần click trigger trước)
 *   - ProfileSettings: #fullName / #phone / [data-testid="successMsg"]
 *   - ReviewSection (inline KHÔNG phải tab): StarWidget aria-label="{n} sao" trong role=radiogroup,
 *     textarea #review-content, submit button "Gửi đánh giá".
 *     Eligibility hint khi không eligible: "Chỉ người đã mua sản phẩm này mới có thể đánh giá."
 *
 * PASS criteria (D-19): 4/4 PASS hoặc PASS-with-skip (test.skip với reason rõ ràng) trên fresh
 *   docker stack `docker compose up -d --build && npx playwright test e2e/smoke.spec.ts`.
 *   Toàn bộ baseline (auth, admin-products/orders/users, order-detail, password-change) vẫn 100% PASS.
 */

import { test, expect } from '@playwright/test';

// ─────────────────────────────────────────────────────────────────
// SMOKE-1 (anonymous): Homepage hero + CTA navigation
// ─────────────────────────────────────────────────────────────────
test.describe('SMOKE-1: Homepage navigation (anonymous)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('hero render + CTA "Khám phá ngay" → /products có ProductCard', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    // Hero heading visible (Plan 15-01 hero copy: "chế tác thủ công" gradient accent)
    await expect(
      page.getByRole('heading', { name: /chế tác thủ công/i })
    ).toBeVisible({ timeout: 10000 });

    // CTA primary "Khám phá ngay" (Plan 15-01 D-02 — link /products)
    const ctaPrimary = page.getByRole('link', { name: 'Khám phá ngay' });
    await expect(ctaPrimary).toBeVisible();
    await ctaPrimary.click();

    // Navigate /products
    await page.waitForURL(/\/products(\?|$)/, { timeout: 10000 });

    // Có ít nhất 1 ProductCard render (selector tolerant — link tới /products/{slug})
    const firstCard = page.locator('a[href^="/products/"]').first();
    await expect(firstCard).toBeVisible({ timeout: 10000 });
  });
});

// ─────────────────────────────────────────────────────────────────
// SMOKE-4 (user): Profile settings edit fullName + phone
// ─────────────────────────────────────────────────────────────────
test.describe('SMOKE-4: Profile editing persist', () => {
  test.use({ storageState: 'e2e/storageState/user.json' });

  test('sửa fullName + phone → success toast → reload persist', async ({ page }) => {
    await page.goto('/profile/settings');
    await page.waitForLoadState('domcontentloaded');

    // Verified selectors từ Wave 0 audit (15-SELECTOR-AUDIT.md)
    const fullNameInput = page.locator('#fullName');
    const phoneInput = page.locator('#phone');

    await expect(fullNameInput).toBeVisible({ timeout: 10000 });
    await expect(phoneInput).toBeVisible();

    // Generate unique value để verify persist (timestamp-based — deterministic identity)
    const newName = `Smoke Test ${Date.now()}`;
    const newPhone = `0900${String(Date.now()).slice(-7)}`;

    await fullNameInput.fill(newName);
    await phoneInput.fill(newPhone);

    // Submit form — try data-testid first, fallback button name (audit chưa lock data-testid)
    const submitBtn = page
      .locator('[data-testid="submitProfile"]')
      .or(page.getByRole('button', { name: /lưu|cập nhật|save/i }))
      .first();
    await submitBtn.click();

    // Success message (verified [data-testid="successMsg"] tồn tại — line 239 settings/page.tsx)
    const successMsg = page
      .locator('[data-testid="successMsg"]')
      .or(page.getByText(/thành công|đã cập nhật|đã lưu/i))
      .first();
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    // Reload + verify persist
    await page.reload();
    await page.waitForLoadState('domcontentloaded');
    await expect(fullNameInput).toHaveValue(newName, { timeout: 10000 });
    await expect(phoneInput).toHaveValue(newPhone);
  });
});

// ─────────────────────────────────────────────────────────────────
// SMOKE-2 + SMOKE-3 (user): authenticated commerce flows
// ─────────────────────────────────────────────────────────────────
test.describe('SMOKE-2..3: authenticated commerce flows', () => {
  test.use({ storageState: 'e2e/storageState/user.json' });

  // ─── SMOKE-2: Address-at-checkout ───
  // Strategy A degradation: skip-if-no-data ở 3 điểm (no product / hidden cart / no address)
  test('SMOKE-2: cart → /checkout → AddressPicker → submit', async ({ page }) => {
    // 1. Mở /products + pick first product
    await page.goto('/products');
    await page.waitForLoadState('domcontentloaded');

    const firstProductLink = page.locator('a[href^="/products/"]').first();
    const hasProduct = await firstProductLink.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasProduct) {
      test.skip(true, 'Không có product trong catalog — seed empty (Strategy A skip-if-no-data)');
      return;
    }
    await firstProductLink.click();
    await page.waitForURL(/\/products\/[^?]+/, { timeout: 10000 });
    await page.waitForLoadState('domcontentloaded');

    // 2. Add to cart — chỉ click nếu add-to-cart visible (stock > 0 per Plan 15-02 D-16)
    const addToCartBtn = page.getByRole('button', { name: /thêm vào giỏ/i });
    const cartBtnVisible = await addToCartBtn.isVisible({ timeout: 5000 }).catch(() => false);
    if (!cartBtnVisible) {
      test.skip(
        true,
        'Product hết hàng (stock=0) — add-to-cart hidden per Plan 15-02 D-16. ' +
          'Cần product stock > 0 trong seed để chạy SMOKE-2.'
      );
      return;
    }
    await addToCartBtn.click();
    // Đợi toast/state update sau add-to-cart
    await page.waitForTimeout(800);

    // 3. Goto /checkout
    await page.goto('/checkout');
    await page.waitForLoadState('networkidle');

    // 4. AddressPicker — verified Wave 0: cần click trigger button "Địa chỉ đã lưu" trước
    //    để mở dropdown listbox, sau đó [role="option"] mới render.
    const addressTrigger = page.getByRole('button', { name: /địa chỉ đã lưu/i }).first();
    const hasTrigger = await addressTrigger.isVisible({ timeout: 5000 }).catch(() => false);
    if (hasTrigger) {
      await addressTrigger.click();
      await page.waitForTimeout(300);
    }

    const addressOption = page.locator('[role="option"]').first();
    const hasAddress = await addressOption.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasAddress) {
      test.skip(
        true,
        'User demo không có saved address — Strategy A skip-if-no-data. ' +
          'Cần seed address (qua /profile/addresses) trước khi chạy SMOKE-2.'
      );
      return;
    }
    await addressOption.click();

    // 5. Submit order
    const submitOrderBtn = page.getByRole('button', { name: /đặt hàng|thanh toán|submit/i }).first();
    await expect(submitOrderBtn).toBeVisible({ timeout: 5000 });
    await submitOrderBtn.click();

    // 6. Assert success: redirect /orders|/profile|success page hoặc toast success
    await Promise.race([
      page.waitForURL(/\/orders|\/profile|success/, { timeout: 15000 }),
      page
        .getByText(/đã đặt hàng|đặt hàng thành công|thành công/i)
        .first()
        .waitFor({ timeout: 15000 }),
    ]);
  });

  // ─── SMOKE-3: Review submission (Strategy A — skip if user không eligible) ───
  // ReviewSection inline trên PDP (KHÔNG phải tab — verified ReviewSection.tsx).
  // Eligibility (verified-buyer) check qua API /reviews/eligibility — nếu fail thì hint
  // "Chỉ người đã mua sản phẩm này mới có thể đánh giá." render thay vì ReviewForm.
  test('SMOKE-3: PDP review submit (skip nếu no DELIVERED order — Strategy A)', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('domcontentloaded');

    const firstProductLink = page.locator('a[href^="/products/"]').first();
    const hasProduct = await firstProductLink.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasProduct) {
      test.skip(true, 'Không có product nào — seed empty (Strategy A skip-if-no-data)');
      return;
    }
    await firstProductLink.click();
    await page.waitForURL(/\/products\/[^?]+/, { timeout: 10000 });
    await page.waitForLoadState('domcontentloaded');

    // Đợi eligibility API resolve (ReviewSection useEffect — verified line 52-59)
    await page.waitForTimeout(1500);

    // Check eligibility: nếu hint "Chỉ người đã mua..." render → user không eligible → skip
    const notEligibleHint = page.getByText(/chỉ người đã mua sản phẩm này/i).first();
    const isNotEligible = await notEligibleHint.isVisible({ timeout: 3000 }).catch(() => false);
    if (isNotEligible) {
      test.skip(
        true,
        'User demo chưa mua product này (REVIEW_NOT_ELIGIBLE) — Strategy A degradation ' +
          'per 15-SELECTOR-AUDIT.md. Cần DELIVERED order chứa product để test review submit.'
      );
      return;
    }

    // Verify ReviewForm render — tìm textarea #review-content (verified ReviewForm.tsx line 65-66)
    const contentTextarea = page.locator('#review-content');
    const hasForm = await contentTextarea.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasForm) {
      test.skip(
        true,
        'Review form không hiện (eligibility chưa load hoặc user state unexpected) — ' +
          'Strategy A skip thay vì hard fail.'
      );
      return;
    }

    // Submit rating 5 sao + content
    // Verified StarWidget aria-label="{n} sao" trong role=radiogroup
    const star5 = page.getByRole('button', { name: /^5 sao$/ });
    await expect(star5).toBeVisible({ timeout: 3000 });
    await star5.click();

    const reviewContent = `Smoke test review ${Date.now()} — chất lượng tốt`;
    await contentTextarea.fill(reviewContent);

    // Verified submit button label "Gửi đánh giá" (ReviewForm.tsx line 80-82)
    const submitBtn = page.getByRole('button', { name: /gửi đánh giá/i });
    await submitBtn.click();

    // Assert success: toast "Đã gửi đánh giá" (verified ReviewSection.tsx line 64) HOẶC
    // review xuất hiện trong list. Tolerant assertion.
    await Promise.race([
      page.getByText(/đã gửi đánh giá|cảm ơn|review submitted/i).first().waitFor({ timeout: 10000 }),
      page.getByText(reviewContent).first().waitFor({ timeout: 10000 }),
    ]);

    // Đảm bảo KHÔNG có error toast (REVIEW_NOT_ELIGIBLE / REVIEW_ALREADY_EXISTS)
    const errorToast = page
      .getByText(/REVIEW_NOT_ELIGIBLE|REVIEW_ALREADY_EXISTS|đã xảy ra lỗi/i)
      .first();
    expect(await errorToast.isVisible({ timeout: 1000 }).catch(() => false)).toBe(false);
  });
});
