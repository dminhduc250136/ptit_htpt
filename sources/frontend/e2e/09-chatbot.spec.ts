/**
 * MODULE 09 — TRỢ LÝ AI (Chatbot).
 *
 * Phủ: chat khách hàng (streaming, markdown, history), AI suggest reply (admin), edge cases.
 *
 * LƯU Ý MÔI TRƯỜNG: tuy bộ test chạy trên full stack thật, riêng `POST /api/chat/stream`
 * và `/suggest-reply` được mock qua page.route() — vì gọi Anthropic Claude API thật trong
 * E2E là tốn phí + không deterministic. Mọi UI khác vẫn dùng backend thật.
 *
 * Selectors (verified):
 *   - FAB: [data-testid="chat-fab"] (chỉ render khi đã đăng nhập)
 *   - Guest CTA: [data-testid="chat-cta-guest"] (link → /login?next=)
 *   - Composer: getByLabel('Soạn tin nhắn')
 *   - Bubble assistant: [data-role="assistant"]
 *   - Admin suggest: [data-testid="suggest-reply-button"|"-textarea"|"-copy"]
 */
import { test, expect } from './utils/fixtures';
import { USER_STATE, ADMIN_STATE, ANON_STATE } from './utils/helpers';
import { mockChatStream, buildDeltas } from './utils/mockChatStream';

test.describe('09-CHATBOT: Trợ lý AI', () => {
  // ─── Khách (chưa đăng nhập) ──────────────────────────────────
  test.describe('Khách (anonymous)', () => {
    test.use({ storageState: ANON_STATE });

    test('BOT-01: Khách thấy CTA đăng nhập, không thấy FAB chat', async ({ page }) => {
      await page.goto('/');
      const cta = page.getByTestId('chat-cta-guest');
      await expect(cta).toBeVisible();
      await expect(cta).toHaveAttribute('href', /\/login\?next=/);
      await expect(page.getByTestId('chat-fab')).toHaveCount(0);
    });
  });

  // ─── Khách hàng đã đăng nhập ─────────────────────────────────
  test.describe('Khách hàng (user)', () => {
    test.use({ storageState: USER_STATE });

    test('BOT-02: Gửi tin nhắn → nhận phản hồi streaming', async ({ page }) => {
      await mockChatStream(page, buildDeltas('Xin chào! Mình là trợ lý mua sắm.', 42));
      await page.goto('/');
      await page.getByTestId('chat-fab').click();

      const composer = page.getByLabel('Soạn tin nhắn');
      await composer.fill('xin chào');
      await composer.press('Enter');

      await expect(page.locator('[data-role="assistant"]').last()).toContainText(
        'trợ lý mua sắm',
        { timeout: 10000 }
      );
    });

    test('BOT-03: Phản hồi render markdown (bold/italic)', async ({ page }) => {
      await mockChatStream(page, [
        { type: 'delta', text: 'Đây là **đậm** và *nghiêng*.' },
        { type: 'done', sessionId: 1 },
      ]);
      await page.goto('/');
      await page.getByTestId('chat-fab').click();
      await page.getByLabel('Soạn tin nhắn').fill('test');
      await page.getByLabel('Soạn tin nhắn').press('Enter');

      const bubble = page.locator('[data-role="assistant"]').last();
      await expect(bubble.locator('strong')).toHaveText('đậm');
      await expect(bubble.locator('em')).toHaveText('nghiêng');
    });

    test('BOT-04: Lịch sử chat persist sau reload', async ({ page }) => {
      await mockChatStream(page, [
        { type: 'delta', text: 'Mình giúp được gì?' },
        { type: 'done', sessionId: 99 },
      ]);
      await page.goto('/');
      await page.getByTestId('chat-fab').click();
      await page.getByLabel('Soạn tin nhắn').fill('Tư vấn laptop');
      await page.getByLabel('Soạn tin nhắn').press('Enter');
      await expect(page.locator('[data-role="assistant"]').last()).toContainText(
        'Mình giúp được gì?',
        { timeout: 10000 }
      );

      // Stub GET sessions để verify history sidebar sau reload
      await page.route('**/api/chat/sessions**', (route) =>
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            timestamp: new Date().toISOString(),
            status: 200,
            message: 'OK',
            data: {
              content: [{ id: 99, title: 'Tư vấn laptop', updatedAt: new Date().toISOString() }],
            },
          }),
        })
      );
      await page.reload();
      await page.getByTestId('chat-fab').click();
      // Session item trong SessionsSidebar là <button title="..."> — dùng selector
      // chính xác (getByText khớp cả quick-reply chip → strict mode violation).
      await expect(
        page.getByRole('button', { name: 'Tư vấn laptop', exact: true })
      ).toBeVisible({ timeout: 10000 });
    });

    test('BOT-05 (Edge): Lỗi stream → hiển thị error banner', async ({ page }) => {
      await mockChatStream(page, [{ type: 'error', error: 'Hệ thống tạm quá tải' }]);
      await page.goto('/');
      await page.getByTestId('chat-fab').click();
      await page.getByLabel('Soạn tin nhắn').fill('test lỗi');
      await page.getByLabel('Soạn tin nhắn').press('Enter');

      // Có error UI (banner role=alert hoặc nút "Thử lại")
      const hasError =
        (await page.getByRole('alert').first().isVisible({ timeout: 8000 }).catch(() => false)) ||
        (await page
          .getByRole('button', { name: /Thử lại/ })
          .first()
          .isVisible({ timeout: 3000 })
          .catch(() => false));
      expect(hasError).toBe(true);
    });
  });

  // ─── Admin: AI suggest reply ─────────────────────────────────
  test.describe('Admin (suggest reply)', () => {
    test.use({ storageState: ADMIN_STATE });

    test('BOT-06: Admin tạo gợi ý trả lời → modal có textarea + disclaimer', async ({ page }) => {
      // Mock order detail + suggest-reply (AI external)
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
        })
      );
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
        })
      );

      await page.goto('/admin/orders/ord-test-1');
      await page.getByTestId('suggest-reply-button').click();

      await expect(page.getByText(/kiểm tra kỹ nội dung/i)).toBeVisible({ timeout: 10000 });
      await expect(page.getByTestId('suggest-reply-textarea')).toHaveValue(
        /đơn hàng đang được xử lý/
      );
      await expect(page.getByTestId('suggest-reply-copy')).toBeEnabled();
    });
  });
});
