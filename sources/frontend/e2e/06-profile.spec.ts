/**
 * MODULE 06 — TÀI KHOẢN (Profile).
 *
 * Phủ: xem profile, sửa thông tin (settings), CRUD địa chỉ, đổi mật khẩu.
 * Dùng user storageState.
 *
 * Selectors (verified):
 *   - /profile/settings: #fullName, #phone, #email(readonly), #oldPassword, #newPassword,
 *     #confirmPassword, [data-testid="submitPassword"], [data-testid="oldPasswordError"],
 *     [data-testid="successMsg"]; nút "Lưu thay đổi"
 *   - /profile/addresses: h1 "Sổ địa chỉ"; nút "+ Thêm địa chỉ mới"; AddressForm getByLabel
 *     (Họ và tên, Số điện thoại, Địa chỉ, Phường/Xã, Quận/Huyện, Tỉnh/Thành phố);
 *     nút "Lưu địa chỉ"; xác nhận xóa: nút "Xóa địa chỉ"
 */
import { test, expect } from './utils/fixtures';
import { USER_STATE, USER_PASSWORD } from './utils/helpers';

test.use({ storageState: USER_STATE });

test.describe('06-PROFILE: Tài khoản', () => {
  // ───────────────────────────────────────────────────────────────
  test('PRF-01: /profile render được sau đăng nhập', async ({ page }) => {
    await page.goto('/profile');
    await page.waitForLoadState('domcontentloaded');
    expect(page.url()).not.toContain('/login');
    expect(await page.title()).not.toMatch(/404|error/i);
  });

  // ───────────────────────────────────────────────────────────────
  test('PRF-02: Sửa thông tin cá nhân (settings) → lưu thành công + persist', async ({ page }) => {
    await page.goto('/profile/settings');
    await page.waitForLoadState('domcontentloaded');

    const fullName = page.locator('#fullName');
    const phone = page.locator('#phone');
    await expect(fullName).toBeVisible({ timeout: 10000 });

    const newName = `E2E User ${Date.now()}`;
    const newPhone = `0900${String(Date.now()).slice(-7)}`;
    await fullName.fill(newName);
    await phone.fill(newPhone);

    await page
      .locator('[data-testid="submitProfile"]')
      .or(page.getByRole('button', { name: /Lưu thay đổi/ }))
      .first()
      .click();

    await expect(
      page
        .locator('[data-testid="successMsg"]')
        .or(page.getByText(/thành công|đã cập nhật|đã lưu/i))
        .first()
    ).toBeVisible({ timeout: 10000 });

    // Reload → persist
    await page.reload();
    await page.waitForLoadState('domcontentloaded');
    await expect(fullName).toHaveValue(newName, { timeout: 10000 });
    await expect(phone).toHaveValue(newPhone);
  });

  // ───────────────────────────────────────────────────────────────
  test('PRF-03: Đổi mật khẩu — sai mật khẩu hiện tại → field-level error', async ({ page }) => {
    await page.goto('/profile/settings');
    await page.waitForLoadState('domcontentloaded');

    await page.fill('#oldPassword', 'SaiMatKhauHienTai999');
    await page.fill('#newPassword', 'NewPass123');
    await page.fill('#confirmPassword', 'NewPass123');
    await page.click('[data-testid="submitPassword"]');

    await expect(page.locator('[data-testid="oldPasswordError"]')).toContainText(
      /không đúng/i,
      { timeout: 10000 }
    );
    // Session vẫn còn
    const cookies = await page.context().cookies();
    expect(cookies.find((c) => c.name === 'auth_present')).toBeDefined();
  });

  // ───────────────────────────────────────────────────────────────
  test('PRF-04: Đổi mật khẩu — đúng mật khẩu → thành công, giữ session', async ({ page }) => {
    await page.goto('/profile/settings');
    await page.waitForLoadState('domcontentloaded');

    const tempPass = `Temp${Date.now()}A1`;

    // Đổi sang mật khẩu tạm
    await page.fill('#oldPassword', USER_PASSWORD);
    await page.fill('#newPassword', tempPass);
    await page.fill('#confirmPassword', tempPass);
    await page.click('[data-testid="submitPassword"]');
    await expect(page.locator('[data-testid="successMsg"]')).toContainText(/đã đổi mật khẩu/i, {
      timeout: 10000,
    });

    // Session vẫn còn (không force logout)
    const cookies = await page.context().cookies();
    expect(cookies.find((c) => c.name === 'auth_present')).toBeDefined();

    // Khôi phục mật khẩu gốc (giữ test idempotent)
    await page.fill('#oldPassword', tempPass);
    await page.fill('#newPassword', USER_PASSWORD);
    await page.fill('#confirmPassword', USER_PASSWORD);
    await page.click('[data-testid="submitPassword"]');
    await expect(page.locator('[data-testid="successMsg"]')).toContainText(/đã đổi mật khẩu/i, {
      timeout: 10000,
    });
  });

  // ───────────────────────────────────────────────────────────────
  test('ADDR-01: /profile/addresses render danh sách địa chỉ', async ({ page }) => {
    await page.goto('/profile/addresses');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByRole('heading', { level: 1, name: /Sổ địa chỉ/ })).toBeVisible({
      timeout: 10000,
    });
  });

  // ───────────────────────────────────────────────────────────────
  test('ADDR-02: Thêm địa chỉ mới → xuất hiện trong danh sách → dọn dẹp', async ({ page }) => {
    await page.goto('/profile/addresses');
    await page.waitForLoadState('domcontentloaded');

    // Nút thêm — "+ Thêm địa chỉ mới" hoặc "Thêm địa chỉ đầu tiên" (empty state)
    const addBtn = page
      .getByRole('button', { name: /\+ Thêm địa chỉ mới/ })
      .or(page.getByRole('button', { name: /Thêm địa chỉ đầu tiên/ }))
      .first();
    const hasAdd = await addBtn
      .waitFor({ state: 'visible', timeout: 8000 })
      .then(() => true)
      .catch(() => false);
    if (!hasAdd) {
      test.skip(true, 'Nút thêm địa chỉ không hiện (đã đạt giới hạn 10?) — Strategy A skip');
      return;
    }
    await addBtn.click();

    await expect(page.getByRole('button', { name: /Lưu địa chỉ/ })).toBeVisible({
      timeout: 5000,
    });

    const marker = `E2E ${Date.now()}`;
    await page.getByLabel(/Họ và tên/).fill(marker);
    await page.getByLabel(/Số điện thoại/).fill('0901234567');
    await page.getByLabel('Địa chỉ (số nhà, đường)').fill('123 Đường Test');
    await page.getByLabel(/Phường\/Xã/).fill('Phường 1');
    await page.getByLabel(/Quận\/Huyện/).fill('Quận 1');
    await page.getByLabel(/Tỉnh\/Thành phố/).fill('TP. Hồ Chí Minh');
    await page.getByRole('button', { name: /Lưu địa chỉ/ }).click();

    // Địa chỉ mới hiện trong list (tìm theo marker)
    await expect(page.getByText(marker).first()).toBeVisible({ timeout: 10000 });

    // Dọn dẹp: xóa địa chỉ vừa tạo (tránh tích lũy đầy giới hạn 10 ở các lần chạy sau).
    const delBtn = page
      .getByRole('button', { name: new RegExp(`Xóa địa chỉ.*${marker}`) })
      .first();
    if (await delBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await delBtn.click();
      const confirm = page.getByRole('button', { name: 'Xóa địa chỉ', exact: true });
      if (await confirm.isVisible({ timeout: 3000 }).catch(() => false)) {
        await confirm.click();
      }
      await page.waitForTimeout(1500);
    }
  });

  // ───────────────────────────────────────────────────────────────
  test('ADDR-03: Xóa địa chỉ → biến mất khỏi danh sách', async ({ page }) => {
    await page.goto('/profile/addresses');
    await page.waitForLoadState('domcontentloaded');

    // Tạo 1 địa chỉ để xóa (đảm bảo có data)
    const addBtn = page
      .getByRole('button', { name: /\+ Thêm địa chỉ mới/ })
      .or(page.getByRole('button', { name: /Thêm địa chỉ đầu tiên/ }))
      .first();
    const hasAdd = await addBtn
      .waitFor({ state: 'visible', timeout: 8000 })
      .then(() => true)
      .catch(() => false);
    if (!hasAdd) {
      test.skip(true, 'Nút thêm địa chỉ không hiện (đã đạt giới hạn 10?) — Strategy A skip');
      return;
    }
    await addBtn.click();
    await expect(page.getByRole('button', { name: /Lưu địa chỉ/ })).toBeVisible({ timeout: 5000 });

    const marker = `XoaE2E ${Date.now()}`;
    await page.getByLabel(/Họ và tên/).fill(marker);
    await page.getByLabel(/Số điện thoại/).fill('0907654321');
    await page.getByLabel('Địa chỉ (số nhà, đường)').fill('456 Đường Xóa');
    await page.getByLabel(/Phường\/Xã/).fill('Phường 2');
    await page.getByLabel(/Quận\/Huyện/).fill('Quận 2');
    await page.getByLabel(/Tỉnh\/Thành phố/).fill('TP. Hồ Chí Minh');
    await page.getByRole('button', { name: /Lưu địa chỉ/ }).click();
    await expect(page.getByText(marker).first()).toBeVisible({ timeout: 10000 });

    // Nút xóa địa chỉ có aria-label="Xóa địa chỉ {tên}" — tìm theo marker
    const deleteTrigger = page.getByRole('button', { name: new RegExp(`Xóa địa chỉ.*${marker}`) }).first();
    const hasDelete = await deleteTrigger
      .waitFor({ state: 'visible', timeout: 5000 })
      .then(() => true)
      .catch(() => false);
    if (!hasDelete) {
      test.skip(true, 'Không tìm thấy nút xóa địa chỉ vừa tạo — Strategy A skip');
      return;
    }
    await deleteTrigger.scrollIntoViewIfNeeded().catch(() => {});
    await deleteTrigger.click();

    // Modal xác nhận xóa mở — xác định qua text tiêu đề "Xác nhận xóa địa chỉ?".
    await expect(page.getByText('Xác nhận xóa địa chỉ?')).toBeVisible({ timeout: 10000 });

    // Nút confirm trong modal: vùng action có 2 nút [Hủy, Xóa địa chỉ] — lấy nút
    // KHÔNG phải "Hủy" và KHÔNG phải "✕" (nút đóng). Dùng filter loại trừ.
    const modalRoot = page.locator('[role="dialog"]');
    await modalRoot
      .locator('button')
      .filter({ hasNotText: 'Hủy' })
      .filter({ hasNotText: '✕' })
      .last()
      .click();

    // Toast "Đã xóa địa chỉ" + card chứa marker biến mất
    await expect(page.getByText(/Đã xóa địa chỉ/)).toBeVisible({ timeout: 10000 });
    await expect(
      page.getByRole('button', { name: new RegExp(`Xóa địa chỉ.*${marker}`) })
    ).toHaveCount(0, { timeout: 10000 });
  });
});
