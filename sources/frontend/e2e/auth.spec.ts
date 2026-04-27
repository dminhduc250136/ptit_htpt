/**
 * Phase 9 / Plan 09-05 (TEST-01) — AUTH tests (3 tests).
 * D-12: auth register / login / logout / role gate.
 *
 * Selectors confirmed từ sources/frontend/src/app/login/page.tsx
 * và sources/frontend/src/app/register/page.tsx:
 * - Login: getByLabel('Email'), getByLabel('Mật khẩu'), button "Đăng nhập"
 * - Register: getByLabel('Tên đăng nhập'), getByLabel('Email'),
 *             getByLabel('Mật khẩu', { exact: true }), button "Tạo tài khoản"
 *
 * Seed credentials (V100__seed_dev_data.sql):
 *   user:  demo@tmdt.local  / admin123
 *   admin: admin@tmdt.local / admin123
 *
 * Anonymous tests — mỗi test tự login/logout, KHÔNG dùng storageState fixture.
 */
import { test, expect } from '@playwright/test';

// Anonymous tests — không reuse storageState (mỗi test tự clear state)
test.use({ storageState: { cookies: [], origins: [] } });

const USER_EMAIL = process.env.E2E_USER_EMAIL ?? 'demo@tmdt.local';
const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? 'admin123';

test('AUTH-1: register flow → user mới đăng nhập được', async ({ page }) => {
  const ts = Date.now();
  const email = `e2e-${ts}@tmdt.local`;
  const username = `e2e${ts}`;
  const password = 'TestPass123';

  await page.goto('/register');
  // Confirmed selectors từ register/page.tsx (dùng Input component với label prop)
  await page.getByLabel('Tên đăng nhập').fill(username);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByLabel('Xác nhận mật khẩu').fill(password);
  await page.getByRole('button', { name: 'Tạo tài khoản' }).click();

  // Register thành công → redirect về / (D-04 trong register/page.tsx: router.replace('/'))
  await page.waitForURL('http://localhost:3000/', { timeout: 15000 });
  // Verify không còn ở /register
  expect(page.url()).not.toContain('/register');
});

test('AUTH-2: login + logout', async ({ page }) => {
  await page.goto('/login');
  // Confirmed selectors từ login/page.tsx
  await page.getByLabel('Email').fill(USER_EMAIL);
  await page.getByLabel('Mật khẩu').fill(USER_PASSWORD);
  await Promise.all([
    page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 15000 }),
    page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(),
  ]);

  // Verify auth_present cookie set
  const cookiesAfterLogin = await page.context().cookies();
  const authPresent = cookiesAfterLogin.find((c) => c.name === 'auth_present');
  expect(authPresent).toBeDefined();

  // Logout: tìm link/button chứa "Đăng xuất" trong navbar
  // Navbar thường hiển thị sau khi AuthProvider hydrate
  const logoutLink = page.getByText(/đăng xuất/i).first();
  if (await logoutLink.isVisible({ timeout: 3000 }).catch(() => false)) {
    await logoutLink.click();
  } else {
    // Fallback: navigate về / và tìm logout trong menu dropdown
    await page.goto('/');
    const logoutBtn = page.getByRole('link', { name: /đăng xuất/i }).first();
    if (await logoutBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await logoutBtn.click();
    }
  }

  // Chờ redirect về / hoặc /login sau logout
  await page.waitForTimeout(1000);
  // Verify cookies cleared sau logout
  const cookiesAfterLogout = await page.context().cookies();
  const authAfterLogout = cookiesAfterLogout.find((c) => c.name === 'auth_present');
  expect(authAfterLogout).toBeUndefined();
});

test('AUTH-3: role gate — chưa login vào /profile/orders → redirect /login; USER vào /admin → /403', async ({ page, context }) => {
  // --- Test chưa login → redirect /login ---
  await context.clearCookies();
  await page.goto('/profile/orders');
  // Middleware (D-02) redirect /profile/* → /login?returnTo=...
  await page.waitForURL(/\/login/, { timeout: 10000 });
  expect(page.url()).toContain('/login');
  expect(page.url()).toContain('returnTo');

  // --- Login user thường → vào /admin → redirect /403 ---
  await page.getByLabel('Email').fill(USER_EMAIL);
  await page.getByLabel('Mật khẩu').fill(USER_PASSWORD);
  await Promise.all([
    page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 15000 }),
    page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(),
  ]);

  await page.goto('/admin');
  // Middleware (D-03): non-ADMIN → redirect /403
  await page.waitForURL(/\/403/, { timeout: 10000 });
  expect(page.url()).toContain('/403');
});
