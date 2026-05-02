/**
 * Phase 9 / Plan 09-05 (TEST-01) — Admin product CRUD (3 tests).
 * D-12: admin list / create / validation error.
 *
 * Selectors confirmed từ sources/frontend/src/app/admin/products/page.tsx:
 * - Heading: "Quản lý sản phẩm" (h1)
 * - Add button: "Thêm sản phẩm" → "+ Thêm sản phẩm" (Button component)
 *   Wait: thực tế render là "+ Thêm sản phẩm" — dùng text exact
 * - Modal form: getByLabel('Tên sản phẩm'), getByLabel('Giá bán'),
 *               getByLabel('Tồn kho'), select "Danh mục"
 * - Submit: Button "Thêm sản phẩm" (trong modal, hoặc "Lưu thay đổi" khi edit)
 * - Toast success: "Sản phẩm đã được thêm thành công"
 *
 * Dùng admin storageState từ global-setup.ts (D-13).
 */
import { test, expect } from '@playwright/test';

test.use({ storageState: 'e2e/storageState/admin.json' });

test('ADM-PROD-1: list /admin/products render heading + table', async ({ page }) => {
  await page.goto('/admin/products');
  // Confirmed heading từ page.tsx: <h1>Quản lý sản phẩm</h1>
  await expect(page.getByRole('heading', { name: 'Quản lý sản phẩm' })).toBeVisible({ timeout: 10000 });
  // Table structure (dù có data hay empty state)
  await expect(page.locator('table')).toBeVisible({ timeout: 10000 });
});

test('ADM-PROD-2: create product → toast success', async ({ page }) => {
  const productName = `Test SP ${Date.now()}`;

  await page.goto('/admin/products');
  await expect(page.getByRole('heading', { name: 'Quản lý sản phẩm' })).toBeVisible({ timeout: 10000 });

  // Confirmed button text từ page.tsx: <Button onClick={openAddModal}>+ Thêm sản phẩm</Button>
  await page.getByRole('button', { name: '+ Thêm sản phẩm' }).click();

  // Modal mở: <h3>Thêm sản phẩm mới</h3>
  await expect(page.getByRole('heading', { name: 'Thêm sản phẩm mới' })).toBeVisible({ timeout: 5000 });

  // Confirmed labels từ modal form (Input component với label prop)
  await page.getByLabel('Tên sản phẩm').fill(productName);
  await page.getByLabel('Giá bán').fill('150000');
  await page.getByLabel('Tồn kho').fill('10');

  // Chọn danh mục (required — nếu không chọn sẽ hiện toast error "Vui lòng chọn danh mục")
  // Chờ categories load xong (loadingCategories state)
  await page.waitForTimeout(1500);
  const categorySelect = page.locator('select').first();
  const categoryCount = await categorySelect.locator('option').count();
  if (categoryCount > 1) {
    // Chọn option thứ 2 (bỏ qua "-- Chọn danh mục --")
    await categorySelect.selectOption({ index: 1 });
  } else {
    // Không có categories → skip test thay vì fail
    test.skip(true, 'Không có danh mục nào trong DB — cần seed categories trước');
  }

  // Submit button trong modal: <Button type="submit">Thêm sản phẩm</Button>
  await page.getByRole('button', { name: 'Thêm sản phẩm', exact: true }).click();

  // Toast success: showToast('Sản phẩm đã được thêm thành công', 'success')
  await expect(page.getByText('Sản phẩm đã được thêm thành công')).toBeVisible({ timeout: 10000 });
});

test('ADM-PROD-3: validation error khi submit form thiếu tên sản phẩm', async ({ page }) => {
  await page.goto('/admin/products');
  await expect(page.getByRole('heading', { name: 'Quản lý sản phẩm' })).toBeVisible({ timeout: 10000 });

  // Mở modal add
  await page.getByRole('button', { name: '+ Thêm sản phẩm' }).click();
  await expect(page.getByRole('heading', { name: 'Thêm sản phẩm mới' })).toBeVisible({ timeout: 5000 });

  // Submit với tên trống → showToast('Vui lòng nhập tên sản phẩm', 'error')
  await page.getByRole('button', { name: 'Thêm sản phẩm', exact: true }).click();

  // Toast error visible (Toast component từ useToast hook)
  await expect(page.getByText('Vui lòng nhập tên sản phẩm')).toBeVisible({ timeout: 5000 });
});
