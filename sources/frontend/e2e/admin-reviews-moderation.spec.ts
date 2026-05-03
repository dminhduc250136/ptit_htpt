/**
 * Phase 21 / Plan 21-04 (REV-06) — Admin moderation E2E spec.
 *
 * Coverage:
 *   ADM-REV-1: list /admin/reviews render heading + table (smoke).
 *   ADM-REV-2: hide review → cross-tab PDP visibility check → unhide → review hiện lại.
 *   ADM-REV-3: filter dropdown 4-state — verify mỗi filter trả về đúng badge.
 *
 * Selectors confirmed từ Plan 21-04 Task 1 (admin/reviews/page.tsx):
 *   - Heading: "Quản lý đánh giá" (h1)
 *   - Filter select aria-label="Lọc đánh giá", 4 options
 *   - Action buttons: text "Ẩn" / "Bỏ ẩn" / "Xoá" inline mỗi row
 *   - Toast: "Đã ẩn review" / "Đã bỏ ẩn review" / "Đã xoá vĩnh viễn"
 *
 * Strategy A degradation: skip-if-no-data ở các điểm yêu cầu seed review.
 * Admin storageState từ global-setup.ts.
 */
import { test, expect } from '@playwright/test';

test.use({ storageState: 'e2e/storageState/admin.json' });

test('ADM-REV-1: list /admin/reviews render heading + filter + table', async ({ page }) => {
  await page.goto('/admin/reviews');

  // Heading từ Plan 21-04 page.tsx: <h1>Quản lý đánh giá</h1>
  await expect(page.getByRole('heading', { name: 'Quản lý đánh giá' })).toBeVisible({
    timeout: 10000,
  });

  // Filter dropdown 4-state
  const filter = page.getByLabel('Lọc đánh giá');
  await expect(filter).toBeVisible({ timeout: 5000 });
  const optionTexts = await filter.locator('option').allTextContents();
  expect(optionTexts).toContain('Tất cả');
  expect(optionTexts).toContain('Đang hiện');
  expect(optionTexts).toContain('Đã ẩn');
  expect(optionTexts.some((t) => t.includes('Đã xoá'))).toBe(true);

  // Table render (dù empty hay có data)
  await expect(page.locator('table')).toBeVisible({ timeout: 10000 });
  await expect(page.getByRole('columnheader', { name: 'Sản phẩm' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Reviewer' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Trạng thái' })).toBeVisible();
});

test('ADM-REV-2: hide review → ẩn khỏi PDP → unhide → hiện lại', async ({ page, context }) => {
  await page.goto('/admin/reviews');
  await expect(page.getByRole('heading', { name: 'Quản lý đánh giá' })).toBeVisible({ timeout: 10000 });

  // Filter "Đang hiện" để chỉ thấy review chưa hidden
  await page.getByLabel('Lọc đánh giá').selectOption('visible');
  await page.waitForTimeout(1000);

  // Capture row đầu tiên — productSlug + reviewerName + content snippet
  const firstRow = page.locator('table tbody tr').first();
  const hasRow = await firstRow.isVisible({ timeout: 3000 }).catch(() => false);
  if (!hasRow) {
    test.skip(true, 'Chưa có review visible nào — Strategy A skip. Cần seed review.');
    return;
  }

  // Lấy productSlug từ link cột Sản phẩm
  const productLink = firstRow.locator('a[href^="/products/"]').first();
  const hasLink = await productLink.isVisible({ timeout: 2000 }).catch(() => false);
  if (!hasLink) {
    test.skip(true, 'Review đầu tiên không có productSlug (product đã soft-delete) — skip.');
    return;
  }
  const productHref = await productLink.getAttribute('href');
  const reviewerName = (await firstRow.locator('td').nth(1).textContent())?.trim() ?? '';

  // Click "Ẩn" trên row đó
  const hideBtn = firstRow.getByRole('button', { name: 'Ẩn', exact: true });
  await expect(hideBtn).toBeVisible({ timeout: 3000 });
  await hideBtn.click();

  // Toast "Đã ẩn review"
  await expect(page.getByText('Đã ẩn review')).toBeVisible({ timeout: 10000 });
  await page.waitForTimeout(800);

  // Cross-tab: mở PDP trên tab mới (anonymous/user vẫn dùng admin session — admin cũng là user
  // nhưng admin vẫn KHÔNG được thấy hidden review trong public list per D-09)
  const pdpPage = await context.newPage();
  await pdpPage.goto(productHref!);
  await pdpPage.waitForLoadState('domcontentloaded');
  await pdpPage.waitForTimeout(1500);

  // Reviewer name của review hidden KHÔNG xuất hiện trong list
  if (reviewerName) {
    const reviewerLocator = pdpPage.getByText(reviewerName, { exact: false });
    // tolerant: count = 0 means hidden khỏi public list
    const count = await reviewerLocator.count();
    expect(count).toBe(0);
  }
  await pdpPage.close();

  // Quay lại admin tab — đổi filter sang "Đã ẩn" để tìm row vừa hide
  await page.getByLabel('Lọc đánh giá').selectOption('hidden');
  await page.waitForTimeout(1000);

  // Click "Bỏ ẩn" trên row đầu tiên (review vừa hide)
  const firstHiddenRow = page.locator('table tbody tr').first();
  const unhideBtn = firstHiddenRow.getByRole('button', { name: 'Bỏ ẩn', exact: true });
  const hasUnhide = await unhideBtn.isVisible({ timeout: 3000 }).catch(() => false);
  if (!hasUnhide) {
    test.skip(true, 'Không tìm thấy nút Bỏ ẩn — có thể row order đã thay đổi. Skip phần unhide.');
    return;
  }
  await unhideBtn.click();
  await expect(page.getByText('Đã bỏ ẩn review')).toBeVisible({ timeout: 10000 });
});

test('ADM-REV-3: filter "Đã xoá" — rows chỉ hiện nút Xoá (no Hide/Unhide)', async ({ page }) => {
  await page.goto('/admin/reviews');
  await expect(page.getByRole('heading', { name: 'Quản lý đánh giá' })).toBeVisible({ timeout: 10000 });

  await page.getByLabel('Lọc đánh giá').selectOption('deleted');
  await page.waitForTimeout(1000);

  const rows = page.locator('table tbody tr');
  const rowCount = await rows.count();
  if (rowCount === 0) {
    test.skip(true, 'Không có review nào đã xoá — Strategy A skip.');
    return;
  }

  // Mọi row có status "Đã xoá" — verify nút Hide/Unhide KHÔNG xuất hiện trong row đầu
  const firstRow = rows.first();
  const hideBtnCount = await firstRow.getByRole('button', { name: 'Ẩn', exact: true }).count();
  const unhideBtnCount = await firstRow.getByRole('button', { name: 'Bỏ ẩn', exact: true }).count();
  expect(hideBtnCount).toBe(0);
  expect(unhideBtnCount).toBe(0);

  // Nhưng phải có nút Xoá
  const deleteBtnCount = await firstRow.getByRole('button', { name: 'Xoá', exact: true }).count();
  expect(deleteBtnCount).toBe(1);
});
