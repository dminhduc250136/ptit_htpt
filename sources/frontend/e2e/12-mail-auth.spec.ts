/**
 * MODULE 12 — EMAIL AUTH (Phase 27).
 *
 * Phủ 3 trang FE auth của Phase 27 cộng link "Quên mật khẩu?" ở login:
 *   - /forgot-password   — form email → success panel
 *   - /verify-email      — 4 state (loading | success | expired | invalid)
 *   - /reset-password    — token gate + client-side validation + success
 *
 * Khác mọi spec trước đó: tests này dùng `page.route` mock toàn bộ
 * `/api/users/auth/**` → KHÔNG cần backend chạy. Chỉ cần Next dev server.
 *
 * Lý do: theo 27-HUMAN-UAT.md, các item này yêu cầu xác minh runtime
 * render (Suspense + useSearchParams + state machine + client validation)
 * — không cần email/SMTP thật. Spec này biến 4/5 mục human-UAT thành
 * automated check. Mục 4 (E2E SMTP thật) vẫn cần inbox người dùng.
 */
import { test, expect } from './utils/fixtures';
import { ANON_STATE } from './utils/helpers';

test.use({ storageState: ANON_STATE });

const AUTH_API = '**/api/users/auth/**';

test.describe('12-MAIL-AUTH: Email auth pages (Phase 27)', () => {
  // ───────────────────────────────────────────────────────────────
  test('MAIL-FE-01: /forgot-password — submit valid email → success panel', async ({ page }) => {
    let postedBody: { email: string } | null = null;

    await page.route(AUTH_API, async (route) => {
      const url = route.request().url();
      if (url.includes('/password/forgot')) {
        postedBody = JSON.parse(route.request().postData() ?? '{}');
        await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
      } else {
        await route.continue();
      }
    });

    await page.goto('/forgot-password');
    await expect(page.getByRole('heading', { name: /Quên mật khẩu/i })).toBeVisible();

    const email = `e2e-${Date.now()}@tmdt.local`;
    await page.getByLabel('Email').fill(email);
    await page.getByRole('button', { name: /Gửi link đặt lại mật khẩu/i }).click();

    // Anti-enumeration: API luôn 200 → success panel hiện
    await expect(page.getByRole('heading', { name: /Kiểm tra hộp thư của bạn/i })).toBeVisible({
      timeout: 5000,
    });
    // Email phải hiện in đậm trong subtitle
    await expect(page.locator('strong', { hasText: email })).toBeVisible();
    expect(postedBody).toEqual({ email });
  });

  // ───────────────────────────────────────────────────────────────
  test('MAIL-FE-01b: /forgot-password — email rỗng/sai format → client-side error, không gọi API', async ({
    page,
  }) => {
    let apiCalled = false;
    await page.route(AUTH_API, async (route) => {
      if (route.request().url().includes('/password/forgot')) apiCalled = true;
      await route.fulfill({ status: 200, body: '{}' });
    });

    await page.goto('/forgot-password');

    // Empty submit
    await page.getByRole('button', { name: /Gửi link đặt lại mật khẩu/i }).click();
    await expect(page.getByText('Vui lòng nhập email')).toBeVisible();
    expect(apiCalled).toBe(false);

    // Wrong format
    await page.getByLabel('Email').fill('not-an-email');
    await page.getByRole('button', { name: /Gửi link đặt lại mật khẩu/i }).click();
    await expect(page.getByText('Email không hợp lệ')).toBeVisible();
    expect(apiCalled).toBe(false);
  });

  // ───────────────────────────────────────────────────────────────
  test('MAIL-FE-02a: /verify-email không token → state "invalid" hiện ngay (không spinner vô tận)', async ({
    page,
  }) => {
    await page.goto('/verify-email');
    await expect(page.getByRole('heading', { name: 'Link không hợp lệ' })).toBeVisible({
      timeout: 3000,
    });
    // Không có spinner ở trạng thái cuối
    await expect(page.locator('[aria-label="Đang xử lý"]')).toHaveCount(0);
  });

  // ───────────────────────────────────────────────────────────────
  test('MAIL-FE-02b: /verify-email?token=ok (mock 200) → state "success"', async ({ page }) => {
    await page.route(AUTH_API, async (route) => {
      if (route.request().url().includes('/verify-email')) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
      } else {
        await route.continue();
      }
    });

    await page.goto('/verify-email?token=validtoken123');
    await expect(page.getByRole('heading', { name: /Email đã được xác minh/i })).toBeVisible({
      timeout: 5000,
    });
    await expect(page.getByRole('button', { name: /Đến trang đăng nhập/i })).toBeVisible();
  });

  // ───────────────────────────────────────────────────────────────
  test('MAIL-FE-02c: /verify-email?token=expired (mock 410) → state "expired"', async ({
    page,
  }) => {
    await page.route(AUTH_API, async (route) => {
      if (route.request().url().includes('/verify-email')) {
        await route.fulfill({ status: 410, contentType: 'application/json', body: '{}' });
      } else {
        await route.continue();
      }
    });

    await page.goto('/verify-email?token=expiredtoken');
    await expect(page.getByRole('heading', { name: /Link xác minh đã hết hạn/i })).toBeVisible({
      timeout: 5000,
    });
    await expect(page.getByRole('button', { name: /Gửi lại email xác minh/i })).toBeVisible();
  });

  // ───────────────────────────────────────────────────────────────
  test('MAIL-FE-02d: /verify-email?token=invalid (mock 400) → state "invalid"', async ({
    page,
  }) => {
    await page.route(AUTH_API, async (route) => {
      if (route.request().url().includes('/verify-email')) {
        await route.fulfill({ status: 400, contentType: 'application/json', body: '{}' });
      } else {
        await route.continue();
      }
    });

    await page.goto('/verify-email?token=badtoken');
    await expect(page.getByRole('heading', { name: 'Link không hợp lệ' })).toBeVisible({
      timeout: 5000,
    });
    await expect(page.getByRole('button', { name: /Quay lại trang chủ/i })).toBeVisible();
  });

  // ───────────────────────────────────────────────────────────────
  test('MAIL-FE-03a: /reset-password không token → status card "Link đặt lại mật khẩu không hợp lệ"', async ({
    page,
  }) => {
    await page.goto('/reset-password');
    await expect(
      page.getByRole('heading', { name: /Link đặt lại mật khẩu không hợp lệ/i })
    ).toBeVisible({ timeout: 3000 });
    // Không render form
    await expect(page.getByLabel('Mật khẩu mới')).toHaveCount(0);
  });

  // ───────────────────────────────────────────────────────────────
  test('MAIL-FE-03b: /reset-password — mật khẩu < 6 ký tự → client-side error', async ({ page }) => {
    let apiCalled = false;
    await page.route(AUTH_API, async (route) => {
      if (route.request().url().includes('/password/reset')) apiCalled = true;
      await route.fulfill({ status: 200, body: '{}' });
    });

    await page.goto('/reset-password?token=abc');
    await page.getByLabel('Mật khẩu mới').fill('abc');
    await page.getByLabel('Xác nhận mật khẩu').fill('abc');
    await page.getByRole('button', { name: /^Đặt lại mật khẩu$/ }).click();

    await expect(page.getByText('Mật khẩu ít nhất 6 ký tự')).toBeVisible();
    expect(apiCalled).toBe(false);
  });

  // ───────────────────────────────────────────────────────────────
  test('MAIL-FE-03c: /reset-password — 2 mật khẩu không khớp → client-side error', async ({
    page,
  }) => {
    let apiCalled = false;
    await page.route(AUTH_API, async (route) => {
      if (route.request().url().includes('/password/reset')) apiCalled = true;
      await route.fulfill({ status: 200, body: '{}' });
    });

    await page.goto('/reset-password?token=abc');
    await page.getByLabel('Mật khẩu mới').fill('abcdef');
    await page.getByLabel('Xác nhận mật khẩu').fill('xyz123');
    await page.getByRole('button', { name: /^Đặt lại mật khẩu$/ }).click();

    await expect(page.getByText('Mật khẩu không khớp')).toBeVisible();
    expect(apiCalled).toBe(false);
  });

  // ───────────────────────────────────────────────────────────────
  test('MAIL-FE-03d: /reset-password — submit hợp lệ (mock 200) → success panel + CTA đăng nhập', async ({
    page,
  }) => {
    let postedBody: { token: string; newPassword: string } | null = null;
    await page.route(AUTH_API, async (route) => {
      if (route.request().url().includes('/password/reset')) {
        postedBody = JSON.parse(route.request().postData() ?? '{}');
        await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
      } else {
        await route.continue();
      }
    });

    await page.goto('/reset-password?token=goodtoken');
    await page.getByLabel('Mật khẩu mới').fill('NewPass123');
    await page.getByLabel('Xác nhận mật khẩu').fill('NewPass123');
    await page.getByRole('button', { name: /^Đặt lại mật khẩu$/ }).click();

    await expect(page.getByRole('heading', { name: /Mật khẩu đã được đặt lại/i })).toBeVisible({
      timeout: 5000,
    });
    await expect(page.getByRole('button', { name: /Đăng nhập ngay/i })).toBeVisible();
    expect(postedBody).toEqual({ token: 'goodtoken', newPassword: 'NewPass123' });
  });

  // ───────────────────────────────────────────────────────────────
  test('MAIL-FE-03e: /reset-password — API trả 410 → chuyển sang token-invalid card', async ({
    page,
  }) => {
    await page.route(AUTH_API, async (route) => {
      if (route.request().url().includes('/password/reset')) {
        await route.fulfill({ status: 410, contentType: 'application/json', body: '{}' });
      } else {
        await route.continue();
      }
    });

    await page.goto('/reset-password?token=expired');
    await page.getByLabel('Mật khẩu mới').fill('NewPass123');
    await page.getByLabel('Xác nhận mật khẩu').fill('NewPass123');
    await page.getByRole('button', { name: /^Đặt lại mật khẩu$/ }).click();

    await expect(
      page.getByRole('heading', { name: /Link đặt lại mật khẩu không hợp lệ/i })
    ).toBeVisible({ timeout: 5000 });
  });

  // ───────────────────────────────────────────────────────────────
  test('MAIL-FE-04: /login có link "Quên mật khẩu?" → điều hướng sang /forgot-password', async ({
    page,
  }) => {
    await page.goto('/login');
    const link = page.getByRole('link', { name: /Quên mật khẩu/i });
    await expect(link).toBeVisible();
    await link.click();
    await expect(page).toHaveURL(/\/forgot-password$/);
    await expect(page.getByRole('heading', { name: /Quên mật khẩu/i })).toBeVisible();
  });
});
