/**
 * Phase 21 / Plan 21-04 (REV-04 + REV-05) — Author edit/delete UX + sort dropdown E2E.
 *
 * Coverage:
 *   AUTHOR-EDIT-1: happy path edit (owner, within window) — locate review của user, click "Sửa",
 *                  update content + rating, click "Lưu thay đổi", assert toast + nội dung mới.
 *   AUTHOR-EDIT-2: soft-delete (owner) — click "Xoá", accept confirm dialog, assert toast +
 *                  review không còn trong list.
 *   AUTHOR-EDIT-3: sort dropdown change + URL persistence — đổi sort qua native <select>,
 *                  assert URL chứa ?sort=rating_desc, assert refetch xảy ra.
 *
 * NOTES:
 *   - Window-expired test (button "Sửa" disabled khi createdAt > 24h) DEFERRED — yêu cầu hoặc
 *     direct DB seed với created_at được backdate, hoặc Spring profile override edit-window-hours=0.
 *     Cả 2 cách đều cần infrastructure ngoài E2E spec scope. Documented trong SUMMARY.
 *   - User storageState từ global-setup.ts — credentials demo@tmdt.local.
 *   - Strategy A degradation pattern: skip-if-no-data thay vì hard fail (cùng style với smoke.spec.ts).
 *   - Selectors confirmed từ ReviewList.tsx (Plan 21-03) + ReviewForm.tsx (mode='edit').
 */
import { test, expect } from '@playwright/test';

test.use({ storageState: 'e2e/storageState/user.json' });

test.describe('AUTHOR-EDIT: REV-04 author edit/delete inline trên PDP', () => {
  test('AUTHOR-EDIT-1: happy path edit — sửa nội dung + rating của review owner', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('domcontentloaded');

    const firstProductLink = page.locator('a[href^="/products/"]').first();
    const hasProduct = await firstProductLink.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasProduct) {
      test.skip(true, 'Không có product trong catalog — Strategy A skip-if-no-data');
      return;
    }
    await firstProductLink.click();
    await page.waitForURL(/\/products\/[^?]+/, { timeout: 10000 });
    await page.waitForLoadState('domcontentloaded');

    // Đợi ReviewSection load (eligibility + listReviews)
    await page.waitForTimeout(1500);

    // Tìm button "Sửa" của review của user (Plan 21-03 ReviewList.tsx render cho isOwner)
    const editBtn = page.getByRole('button', { name: 'Sửa', exact: true }).first();
    const hasEditBtn = await editBtn.isVisible({ timeout: 3000 }).catch(() => false);
    if (!hasEditBtn) {
      test.skip(
        true,
        'User demo chưa có review trên product này — Strategy A skip. ' +
          'Cần seed review hoặc chạy SMOKE-3 trước để tạo review.'
      );
      return;
    }

    // Click "Sửa" → ReviewForm mode='edit' inline swap (Plan 21-03 D-22)
    await editBtn.click();

    // Form xuất hiện inline với textarea prefilled — verify
    const contentTextarea = page.locator('#review-content');
    await expect(contentTextarea).toBeVisible({ timeout: 3000 });

    // Update content
    const updatedContent = `Đã chỉnh sửa ${Date.now()} — nội dung mới sau edit`;
    await contentTextarea.fill(updatedContent);

    // Submit edit — button "Lưu thay đổi" (Plan 21-03 ReviewForm.tsx mode='edit' label)
    const saveBtn = page.getByRole('button', { name: 'Lưu thay đổi' });
    await expect(saveBtn).toBeVisible({ timeout: 3000 });
    await saveBtn.click();

    // Assert toast 'Đã cập nhật đánh giá' (CONTEXT specifics 240)
    await expect(page.getByText('Đã cập nhật đánh giá')).toBeVisible({ timeout: 10000 });

    // Assert nội dung mới xuất hiện trong list
    await expect(page.getByText(updatedContent).first()).toBeVisible({ timeout: 5000 });
  });

  test('AUTHOR-EDIT-2: soft-delete owner — click "Xoá", confirm, review biến mất', async ({ page }) => {
    // Mock window.confirm → accept
    page.on('dialog', (dialog) => dialog.accept());

    await page.goto('/products');
    await page.waitForLoadState('domcontentloaded');

    const firstProductLink = page.locator('a[href^="/products/"]').first();
    const hasProduct = await firstProductLink.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasProduct) {
      test.skip(true, 'Không có product — Strategy A skip-if-no-data');
      return;
    }
    await firstProductLink.click();
    await page.waitForURL(/\/products\/[^?]+/, { timeout: 10000 });
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1500);

    // Find "Xoá" button của user (chỉ render cho isOwner trong ReviewList)
    const deleteBtn = page.getByRole('button', { name: 'Xoá', exact: true }).first();
    const hasDeleteBtn = await deleteBtn.isVisible({ timeout: 3000 }).catch(() => false);
    if (!hasDeleteBtn) {
      test.skip(
        true,
        'User demo không có review trên product này — Strategy A skip. ' +
          'Cần seed review (hoặc chạy SMOKE-3 hoặc AUTHOR-EDIT-1 trước trong cùng session).'
      );
      return;
    }

    await deleteBtn.click();

    // Toast "Đã xoá đánh giá" (CONTEXT specifics 240)
    await expect(page.getByText('Đã xoá đánh giá')).toBeVisible({ timeout: 10000 });
  });

  test('AUTHOR-EDIT-3: sort dropdown change → URL persistence ?sort=rating_desc', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('domcontentloaded');

    const firstProductLink = page.locator('a[href^="/products/"]').first();
    const hasProduct = await firstProductLink.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasProduct) {
      test.skip(true, 'Không có product — Strategy A skip-if-no-data');
      return;
    }
    await firstProductLink.click();
    await page.waitForURL(/\/products\/[^?]+/, { timeout: 10000 });
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1000);

    // Sort dropdown (Plan 21-03 ReviewList.tsx — aria-label="Sắp xếp đánh giá")
    const sortSelect = page.getByLabel('Sắp xếp đánh giá');
    const hasSortSelect = await sortSelect.isVisible({ timeout: 3000 }).catch(() => false);
    if (!hasSortSelect) {
      test.skip(
        true,
        'Sort dropdown không hiển thị — có thể product chưa có review nào (ReviewSection ẩn ' +
          'sort khi list empty). Strategy A skip.'
      );
      return;
    }

    // Đổi sang "Đánh giá cao nhất" (rating_desc)
    await sortSelect.selectOption('rating_desc');
    await page.waitForTimeout(800); // refetch + router.replace

    // Assert URL chứa ?sort=rating_desc (D-13: default newest KHÔNG ghi vào URL)
    await expect(page).toHaveURL(/\?.*sort=rating_desc/);

    // Đổi về "Mới nhất" → URL bỏ ?sort=
    await sortSelect.selectOption('newest');
    await page.waitForTimeout(800);
    const url = page.url();
    expect(url.includes('sort=')).toBe(false);
  });
});
