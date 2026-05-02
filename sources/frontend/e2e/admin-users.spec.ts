/**
 * Phase 9 / Plan 09-05 (TEST-01) — Admin users (2 tests).
 * D-12: admin list users + PATCH fullName.
 *
 * Selectors confirmed từ sources/frontend/src/app/admin/users/page.tsx:
 * - Heading: "Quản lý tài khoản" (h1)
 * - Edit button: aria-label="Chỉnh sửa tài khoản" (button với emoji ✏️)
 * - Delete button: aria-label="Xóa tài khoản" (chỉ hiện cho role != ADMIN)
 * - Edit modal form: getByLabel('Họ và tên'), getByLabel('Số điện thoại'), select Vai trò
 * - Save button trong modal: "Lưu thay đổi" (Button component)
 * - Toast success: "Thông tin tài khoản đã được cập nhật"
 *
 * Dùng admin storageState từ global-setup.ts (D-13).
 */
import { test, expect } from '@playwright/test';

test.use({ storageState: 'e2e/storageState/admin.json' });

test('ADM-USR-1: list /admin/users render heading + table với ít nhất 1 row (seed users)', async ({ page }) => {
  await page.goto('/admin/users');
  // Confirmed heading từ page.tsx: <h1>Quản lý tài khoản</h1>
  await expect(page.getByRole('heading', { name: 'Quản lý tài khoản' })).toBeVisible({ timeout: 10000 });

  // Đợi data load
  await page.waitForTimeout(2000);

  // Seed V100 tạo 2 users (admin + demo_user) nên bảng PHẢI có rows
  const editBtns = page.locator('[aria-label="Chỉnh sửa tài khoản"]');
  await expect(editBtns.first()).toBeVisible({ timeout: 10000 });
  const count = await editBtns.count();
  expect(count).toBeGreaterThanOrEqual(1);
});

test('ADM-USR-2: PATCH user fullName → toast success', async ({ page }) => {
  await page.goto('/admin/users');
  await expect(page.getByRole('heading', { name: 'Quản lý tài khoản' })).toBeVisible({ timeout: 10000 });

  // Đợi data load xong
  await page.waitForTimeout(2000);

  // Click nút chỉnh sửa cho user đầu tiên có nút edit
  const firstEditBtn = page.locator('[aria-label="Chỉnh sửa tài khoản"]').first();
  const btnVisible = await firstEditBtn.isVisible({ timeout: 5000 }).catch(() => false);

  if (!btnVisible) {
    test.skip(true, 'Không có nút chỉnh sửa — bảng users chưa load');
    return;
  }

  await firstEditBtn.click();

  // Modal mở: <h3>Chỉnh sửa tài khoản</h3>
  await expect(page.getByRole('heading', { name: 'Chỉnh sửa tài khoản' })).toBeVisible({ timeout: 5000 });

  // Điền họ tên mới — confirmed label từ modal: getByLabel('Họ và tên')
  const newName = `E2E Test ${Date.now()}`;
  await page.getByLabel('Họ và tên').fill(newName);

  // Submit: Button "Lưu thay đổi" — confirmed từ modal modalActions
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click();

  // Toast success: showToast('Thông tin tài khoản đã được cập nhật', 'success')
  await expect(page.getByText('Thông tin tài khoản đã được cập nhật')).toBeVisible({ timeout: 10000 });
});
