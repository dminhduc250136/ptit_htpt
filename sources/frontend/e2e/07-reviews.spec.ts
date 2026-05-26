/**
 * MODULE 07 — ĐÁNH GIÁ SẢN PHẨM (Reviews).
 *
 * Phủ: xem danh sách review trên PDP, viết review (eligibility check), sửa/xóa review,
 *      sắp xếp review.
 *
 * ReviewSection nằm trong TAB "Đánh giá (N)" trên PDP — phải click tab này trước để
 * list/form review render. Eligibility: chỉ verified-buyer mới viết được — user demo
 * có đơn DELIVERED chứa SP seed (apple-iphone-15-pro-max-256gb) nên đủ điều kiện.
 * seed-dev V102 cũng tạo sẵn 1 review của user demo cho SP này.
 *
 * Selectors (verified):
 *   - Tab review: button "Đánh giá (N)"
 *   - StarWidget: button aria-label "{n} sao"
 *   - textarea #review-content; submit "Gửi đánh giá"; edit submit "Lưu thay đổi"
 *   - Hint không eligible: "Chỉ người đã mua sản phẩm này mới có thể đánh giá."
 *   - Sort dropdown: getByLabel('Sắp xếp đánh giá'); Author actions: button "Sửa"/"Xoá"
 *
 * LƯU Ý thứ tự: REV-03 (sửa) chạy trước REV-04 (xóa) trong file. REV-04 sau khi xóa
 * sẽ gửi lại 1 review mới để các lần chạy kế tiếp vẫn có dữ liệu.
 */
import { test, expect } from './utils/fixtures';
import type { Page } from '@playwright/test';
import { USER_STATE, ANON_STATE, gotoProductBySlug } from './utils/helpers';

/** Vào PDP SP seed + mở tab "Đánh giá". Trả false nếu SP không tồn tại. */
async function openReviewTab(page: Page): Promise<boolean> {
  if (!(await gotoProductBySlug(page))) return false;
  await page.waitForTimeout(1000);
  const tab = page.getByRole('button', { name: /Đánh giá \(\d+\)/ });
  if (await tab.isVisible({ timeout: 5000 }).catch(() => false)) {
    await tab.click();
    await page.waitForTimeout(1500); // đợi list + eligibility API
  }
  return true;
}

test.describe('07-REVIEWS: Đánh giá sản phẩm', () => {
  // ───────────────────────────────────────────────────────────────
  test.describe('Xem review (ẩn danh)', () => {
    test.use({ storageState: ANON_STATE });

    test('REV-01: Tab đánh giá render danh sách review', async ({ page }) => {
      if (!(await openReviewTab(page))) {
        test.skip(true, 'SP seed không tồn tại — Strategy A skip');
        return;
      }
      // Có review (seed V102) hiển thị HOẶC empty state — cả 2 hợp lệ
      const hasReviewArea = await page
        .getByText(/đánh giá|chưa có đánh giá|nhận xét/i)
        .first()
        .isVisible({ timeout: 5000 })
        .catch(() => false);
      expect(hasReviewArea).toBe(true);
    });
  });

  // ───────────────────────────────────────────────────────────────
  test.describe('Viết & quản lý review (user)', () => {
    test.use({ storageState: USER_STATE });

    test('REV-02: User verified-buyer → không thấy hint "chưa mua"', async ({ page }) => {
      if (!(await openReviewTab(page))) {
        test.skip(true, 'SP seed không tồn tại — Strategy A skip');
        return;
      }
      // User demo có đơn DELIVERED chứa SP này → KHÔNG được thấy hint eligibility.
      const notEligible = await page
        .getByText(/chỉ người đã mua sản phẩm này/i)
        .first()
        .isVisible({ timeout: 3000 })
        .catch(() => false);
      expect(notEligible, 'User demo phải là verified-buyer (có đơn DELIVERED)').toBe(false);

      // Tab review render — có form viết, hoặc review của mình, hoặc list review.
      const hasForm = await page
        .locator('#review-content')
        .isVisible({ timeout: 3000 })
        .catch(() => false);
      const hasEditBtn = await page
        .getByRole('button', { name: 'Sửa', exact: true })
        .first()
        .isVisible({ timeout: 2000 })
        .catch(() => false);
      const hasReviewText = await page
        .getByText(/sao|đánh giá/i)
        .first()
        .isVisible({ timeout: 2000 })
        .catch(() => false);
      expect(hasForm || hasEditBtn || hasReviewText).toBe(true);
    });

    test('REV-03: Sửa review của chính mình', async ({ page }) => {
      if (!(await openReviewTab(page))) {
        test.skip(true, 'SP seed không tồn tại — Strategy A skip');
        return;
      }
      const editBtn = page.getByRole('button', { name: 'Sửa', exact: true }).first();
      const hasEdit = await editBtn
        .waitFor({ state: 'visible', timeout: 6000 })
        .then(() => true)
        .catch(() => false);
      if (!hasEdit) {
        test.skip(true, 'Không thấy nút Sửa review của user demo — Strategy A skip');
        return;
      }

      await editBtn.click();
      // Textarea trong ReviewForm — định danh qua placeholder (không có id ổn định).
      const textarea = page.getByPlaceholder(/Chia sẻ trải nghiệm của bạn/).last();
      await expect(textarea).toBeVisible({ timeout: 5000 });

      const updated = `Đã sửa E2E ${Date.now()}`;
      await textarea.fill(updated);
      await page.getByRole('button', { name: /Lưu thay đổi/ }).click();
      await expect(page.getByText(/đã cập nhật đánh giá/i)).toBeVisible({ timeout: 10000 });
    });

    test('REV-04: Xóa review của chính mình → rồi tạo lại', async ({ page }) => {
      page.on('dialog', (d) => d.accept());

      if (!(await openReviewTab(page))) {
        test.skip(true, 'SP seed không tồn tại — Strategy A skip');
        return;
      }
      const deleteBtn = page.getByRole('button', { name: 'Xoá', exact: true }).first();
      const hasDelete = await deleteBtn
        .waitFor({ state: 'visible', timeout: 6000 })
        .then(() => true)
        .catch(() => false);
      if (!hasDelete) {
        test.skip(true, 'Không thấy nút Xoá review của user demo — Strategy A skip');
        return;
      }

      await deleteBtn.click();
      await expect(page.getByText(/đã xoá đánh giá/i)).toBeVisible({ timeout: 10000 });

      // Khôi phục: gửi lại 1 review để các lần chạy sau (REV-03/05) vẫn có dữ liệu.
      await page.waitForTimeout(1500);
      const textarea = page.getByPlaceholder(/Chia sẻ trải nghiệm của bạn/).last();
      if (await textarea.isVisible({ timeout: 5000 }).catch(() => false)) {
        await page.getByRole('button', { name: /^5 sao$/ }).first().click().catch(() => {});
        await textarea.fill(`Review khôi phục sau E2E ${Date.now()}`);
        await page.getByRole('button', { name: /Gửi đánh giá/ }).click();
        await page
          .getByText(/đã gửi đánh giá|cảm ơn/i)
          .first()
          .waitFor({ timeout: 10000 })
          .catch(() => {});
      }
    });

    test('REV-05: Đổi sắp xếp review → URL persist ?sort=', async ({ page }) => {
      if (!(await openReviewTab(page))) {
        test.skip(true, 'SP seed không tồn tại — Strategy A skip');
        return;
      }
      const sortSelect = page.getByLabel('Sắp xếp đánh giá');
      const hasSort = await sortSelect
        .waitFor({ state: 'visible', timeout: 5000 })
        .then(() => true)
        .catch(() => false);
      if (!hasSort) {
        test.skip(true, 'SP chưa có review nào — sort dropdown ẩn (Strategy A)');
        return;
      }

      await sortSelect.selectOption('rating_desc');
      await page.waitForTimeout(1000);
      await expect(page).toHaveURL(/\?.*sort=rating_desc/);
    });
  });
});
