/**
 * MODULE 01 — XÁC THỰC (Authentication).
 *
 * Phủ: đăng ký, đăng nhập, đăng xuất, role gate (route guard), returnTo redirect.
 * Tất cả test ẩn danh — mỗi test tự quản lý state, KHÔNG dùng storageState fixture.
 *
 * Selectors (verified):
 *   - Login:    getByLabel('Email'), getByLabel('Mật khẩu'), button "Đăng nhập"
 *   - Register: getByLabel('Tên đăng nhập'), getByLabel('Email'),
 *               getByLabel('Mật khẩu', {exact:true}), getByLabel('Xác nhận mật khẩu'),
 *               button "Tạo tài khoản"
 *   - auth_present cookie là chỉ báo đăng nhập thành công.
 */
import { test, expect } from './utils/fixtures';
import { ANON_STATE, USER_EMAIL, USER_PASSWORD } from './utils/helpers';

test.use({ storageState: ANON_STATE });

test.describe('01-AUTH: Xác thực', () => {
  // ───────────────────────────────────────────────────────────────
  test('AUTH-01: Đăng ký tài khoản mới → tự động đăng nhập', async ({ page }) => {
    const ts = Date.now();
    const email = `e2e-${ts}@tmdt.local`;

    await page.goto('/register');
    await page.getByLabel('Tên đăng nhập').fill(`e2e${ts}`);
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Mật khẩu', { exact: true }).fill('TestPass123');
    await page.getByLabel('Xác nhận mật khẩu').fill('TestPass123');
    await page.getByRole('button', { name: 'Tạo tài khoản' }).click();

    // Đăng ký thành công → redirect về trang chủ
    await page.waitForURL('http://localhost:3000/', { timeout: 15000, waitUntil: 'domcontentloaded' });
    expect(page.url()).not.toContain('/register');

    // Session đã set
    const cookies = await page.context().cookies();
    expect(cookies.find((c) => c.name === 'auth_present')).toBeDefined();
  });

  // ───────────────────────────────────────────────────────────────
  test('AUTH-02: Đăng ký với email đã tồn tại → báo lỗi', async ({ page }) => {
    await page.goto('/register');
    await page.getByLabel('Tên đăng nhập').fill(`dup${Date.now()}`);
    await page.getByLabel('Email').fill(USER_EMAIL); // email seed đã tồn tại
    await page.getByLabel('Mật khẩu', { exact: true }).fill('TestPass123');
    await page.getByLabel('Xác nhận mật khẩu').fill('TestPass123');
    await page.getByRole('button', { name: 'Tạo tài khoản' }).click();

    // Vẫn ở /register + có thông báo lỗi (email trùng / đã tồn tại)
    await page.waitForTimeout(2000);
    expect(page.url()).toContain('/register');
    await expect(
      page.getByText(/đã tồn tại|đã được sử dụng|đã được đăng ký|đã đăng ký/i).first()
    ).toBeVisible({ timeout: 5000 });
  });

  // ───────────────────────────────────────────────────────────────
  test('AUTH-03: Đăng nhập đúng credentials → vào được hệ thống', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(USER_EMAIL);
    await page.getByLabel('Mật khẩu').fill(USER_PASSWORD);
    await Promise.all([
      page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 15000, waitUntil: 'domcontentloaded' }),
      page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(),
    ]);

    const cookies = await page.context().cookies();
    expect(cookies.find((c) => c.name === 'auth_present')).toBeDefined();
  });

  // ───────────────────────────────────────────────────────────────
  test('AUTH-04: Đăng nhập sai mật khẩu → báo lỗi, không tạo session', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(USER_EMAIL);
    await page.getByLabel('Mật khẩu').fill('SaiMatKhau999999');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();

    // Vẫn ở /login + lỗi hiển thị
    await page.waitForTimeout(2000);
    expect(page.url()).toContain('/login');
    await expect(
      page.getByText(/không chính xác|không đúng|sai|không hợp lệ|thất bại/i).first()
    ).toBeVisible({ timeout: 5000 });

    const cookies = await page.context().cookies();
    expect(cookies.find((c) => c.name === 'auth_present')).toBeUndefined();
  });

  // ───────────────────────────────────────────────────────────────
  test('AUTH-05: Đăng xuất → xóa session', async ({ page }) => {
    // Đăng nhập trước
    await page.goto('/login');
    await page.getByLabel('Email').fill(USER_EMAIL);
    await page.getByLabel('Mật khẩu').fill(USER_PASSWORD);
    await Promise.all([
      page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 15000, waitUntil: 'domcontentloaded' }),
      page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(),
    ]);

    // Đăng xuất nằm trong dropdown "Menu tài khoản" — phải mở menu trước
    const accountMenu = page.getByRole('button', { name: /Menu tài khoản/i });
    await accountMenu.click();
    await page.waitForTimeout(400);

    const logout = page.getByRole('button', { name: /đăng xuất/i }).or(
      page.getByRole('link', { name: /đăng xuất/i })
    ).first();
    await logout.click();

    await page.waitForTimeout(1500);
    const cookies = await page.context().cookies();
    expect(cookies.find((c) => c.name === 'auth_present')).toBeUndefined();
  });

  // ───────────────────────────────────────────────────────────────
  test('AUTH-06: Chưa đăng nhập vào route bảo vệ → redirect /login?returnTo', async ({ page }) => {
    await page.goto('/profile/orders');
    await page.waitForURL(/\/login/, { timeout: 10000, waitUntil: 'domcontentloaded' });
    expect(page.url()).toContain('/login');
    expect(page.url()).toContain('returnTo');
  });

  // ───────────────────────────────────────────────────────────────
  test('AUTH-07: Sau đăng nhập từ returnTo → quay lại trang đích', async ({ page }) => {
    await page.goto('/checkout');
    await page.waitForURL(/\/login/, { timeout: 10000, waitUntil: 'domcontentloaded' });

    await page.getByLabel('Email').fill(USER_EMAIL);
    await page.getByLabel('Mật khẩu').fill(USER_PASSWORD);
    await Promise.all([
      page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 15000, waitUntil: 'domcontentloaded' }),
      page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(),
    ]);

    // returnTo đưa user về /checkout (không phải về trang chủ)
    expect(page.url()).toContain('/checkout');
  });

  // ───────────────────────────────────────────────────────────────
  test('AUTH-08: USER thường vào /admin → redirect /403 (role gate)', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(USER_EMAIL);
    await page.getByLabel('Mật khẩu').fill(USER_PASSWORD);
    await Promise.all([
      page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 15000, waitUntil: 'domcontentloaded' }),
      page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(),
    ]);

    await page.goto('/admin');
    await page.waitForURL(/\/403/, { timeout: 10000, waitUntil: 'domcontentloaded' });
    expect(page.url()).toContain('/403');
  });
});
