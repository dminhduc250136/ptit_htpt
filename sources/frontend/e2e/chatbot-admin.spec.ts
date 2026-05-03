/**
 * Phase 22 / Plan 22-07 — Admin AI suggest-reply E2E.
 *
 * Yêu cầu (REQ AI-05, threat T-22-05):
 *   - Admin click "AI suggest reply" → modal hiện textarea + disclaimer.
 *   - Manual confirm: textarea editable, copy button enabled (D-07 — không auto-confirm).
 *
 * Dùng admin storageState từ global-setup.ts (admin.json).
 * Stub `/api/orders/admin/[id]` + `/api/admin/orders/[id]/suggest-reply`
 * để spec không phụ thuộc backend Spring Boot.
 */
import { test, expect } from '@playwright/test';

test.use({ storageState: 'e2e/storageState/admin.json' });

test.describe('Chatbot — admin suggest reply', () => {
  test('admin click button → modal hiện gợi ý có disclaimer', async ({ page }) => {
    // Stub admin order detail endpoint (Phase 17 contract)
    await page.route('**/api/orders/admin/**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 200,
          message: 'OK',
          data: {
            id: 'ord-test-1',
            userId: 'usr-aaaa-bbbb',
            status: 'PENDING',
            totalAmount: 1000000,
            items: [],
            createdAt: new Date().toISOString(),
          },
        }),
      }),
    );

    // Stub suggest-reply endpoint
    await page.route('**/api/admin/orders/**/suggest-reply', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 200,
          message: 'OK',
          data: {
            text: 'Chào bạn, đơn hàng đang được xử lý và sẽ giao trong 2-3 ngày tới.',
            orderId: 'ord-test-1',
          },
        }),
      }),
    );

    await page.goto('/admin/orders/ord-test-1');
    await page.getByTestId('suggest-reply-button').click();

    // Disclaimer hiện diện — D-07 manual confirm guarantee
    await expect(page.getByText(/kiểm tra kỹ nội dung/i)).toBeVisible({ timeout: 10_000 });
    // Textarea hiện gợi ý đã sinh
    await expect(page.getByTestId('suggest-reply-textarea')).toHaveValue(
      /đơn hàng đang được xử lý/,
    );
    // Copy button enabled — admin có thể paste vào kênh khác
    await expect(page.getByTestId('suggest-reply-copy')).toBeEnabled();
  });
});
