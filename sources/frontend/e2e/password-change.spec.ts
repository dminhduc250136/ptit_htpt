/**
 * Phase 9 / Plan 09-05 (TEST-01) — Password change regression (2 tests).
 * D-12: AUTH-07 happy path + sai oldPassword (regression cho Plan 09-04).
 *
 * Selectors confirmed từ sources/frontend/src/app/profile/settings/page.tsx (Plan 09-04):
 * - input#oldPassword (htmlFor="oldPassword")
 * - input#newPassword (htmlFor="newPassword")
 * - input#confirmPassword (htmlFor="confirmPassword")
 * - data-testid="submitPassword" — submit button
 * - data-testid="oldPasswordError" — field-level error "Mật khẩu hiện tại không đúng"
 * - data-testid="successMsg" — success message "Đã đổi mật khẩu" (role="status")
 * - data-testid="formError" — form-level error
 *
 * Error code confirmed từ 09-03-SUMMARY: backend trả field `code` = 'AUTH_INVALID_PASSWORD'
 * (không phải errorCode). Frontend mapping tại settings/page.tsx đã fix (09-04 deviation R1).
 *
 * D-10: Sau khi đổi password thành công → giữ session, KHÔNG force logout.
 *
 * Dùng user storageState từ global-setup.ts (D-13).
 */
import { test, expect } from '@playwright/test';

test.use({ storageState: 'e2e/storageState/user.json' });

const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? 'admin123';

test('PWD-1: sai oldPassword → field-level error "Mật khẩu hiện tại không đúng"', async ({ page }) => {
  await page.goto('/profile/settings');
  await page.waitForLoadState('domcontentloaded');

  // Confirmed selectors từ settings/page.tsx (htmlFor ids)
  await page.fill('input#oldPassword', 'SaiPasswordKhongDung999');
  await page.fill('input#newPassword', 'NewPass123');
  await page.fill('input#confirmPassword', 'NewPass123');

  // Submit button: data-testid="submitPassword" (plan 09-04 confirmed)
  await page.click('[data-testid="submitPassword"]');

  // Field-level error: data-testid="oldPasswordError"
  // Text: 'Mật khẩu hiện tại không đúng' (confirmed từ settings/page.tsx line: setOldError)
  await expect(page.locator('[data-testid="oldPasswordError"]')).toContainText(
    'Mật khẩu hiện tại không đúng',
    { timeout: 10000 },
  );

  // Verify auth_present cookie vẫn còn (không logout)
  const cookies = await page.context().cookies();
  expect(cookies.find((c) => c.name === 'auth_present')).toBeDefined();
});

test('PWD-2: đúng oldPassword → success message "Đã đổi mật khẩu" + session còn (D-10)', async ({ page }) => {
  await page.goto('/profile/settings');
  await page.waitForLoadState('domcontentloaded');

  // Dùng password tạm thời để đảm bảo test idempotent
  const TEMP_PASSWORD = `Temp${Date.now()}A1`;

  // Bước 1: Đổi sang password tạm
  await page.fill('input#oldPassword', USER_PASSWORD);
  await page.fill('input#newPassword', TEMP_PASSWORD);
  await page.fill('input#confirmPassword', TEMP_PASSWORD);
  await page.click('[data-testid="submitPassword"]');

  // Verify success: data-testid="successMsg", text "Đã đổi mật khẩu" (settings/page.tsx)
  await expect(page.locator('[data-testid="successMsg"]')).toContainText('Đã đổi mật khẩu', {
    timeout: 10000,
  });

  // D-10: session vẫn còn sau khi đổi password (KHÔNG force logout)
  const cookiesAfter = await page.context().cookies();
  expect(cookiesAfter.find((c) => c.name === 'auth_present')).toBeDefined();

  // Bước 2: Restore lại password gốc để test idempotent (các run sau vẫn dùng được)
  // Form đã reset (setOldPassword('') sau success) nên cần điền lại
  await page.fill('input#oldPassword', TEMP_PASSWORD);
  await page.fill('input#newPassword', USER_PASSWORD);
  await page.fill('input#confirmPassword', USER_PASSWORD);
  await page.click('[data-testid="submitPassword"]');

  // Verify restore cũng thành công
  await expect(page.locator('[data-testid="successMsg"]')).toContainText('Đã đổi mật khẩu', {
    timeout: 10000,
  });
});
