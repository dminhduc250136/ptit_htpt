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

// SMOKE-2 + SMOKE-3 → appended in Task 2
