/**
 * Phase 22 / Plan 22-07 — Customer chatbot E2E.
 *
 * Yêu cầu (REQ AI-01, AI-03, AI-04):
 *   1. Happy path streaming bubble.
 *   2. Markdown render: **bold** → <strong>, *italic* → <em>.
 *   3. Guest sees login CTA (no FAB).
 *   4. History persist: gửi tin → reload → sessions sidebar có entry.
 *
 * Dùng mockChatStream() để khỏi cần ANTHROPIC_API_KEY thực.
 * Authenticated tests dùng storageState từ global-setup.ts (user.json).
 */
import { test, expect } from '@playwright/test';
import { mockChatStream, buildDeltas } from './utils/mockChatStream';

test.describe('Chatbot — customer', () => {
  test.use({ storageState: 'e2e/storageState/user.json' });

  test('happy path: user gửi tin nhắn và thấy streaming bubble', async ({ page }) => {
    await mockChatStream(page, buildDeltas('Xin chào! Mình là trợ lý mua sắm.', 42));
    await page.goto('/');
    await page.getByTestId('chat-fab').click();
    const composer = page.getByLabel('Soạn tin nhắn');
    await composer.fill('xin chào');
    await composer.press('Enter');
    await expect(page.locator('[data-role="assistant"]').last()).toContainText(
      'trợ lý mua sắm',
      { timeout: 10_000 },
    );
  });

  test('markdown render: assistant **bold** rendered as <strong>', async ({ page }) => {
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

  test('history persist: send → reload → sessions sidebar có entry', async ({ page }) => {
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
      { timeout: 10_000 },
    );

    // Sau reload: stub GET /api/chat/sessions trả về session vừa tạo
    await page.route('**/api/chat/sessions**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 200,
          message: 'OK',
          data: {
            content: [
              { id: 99, title: 'Tư vấn laptop', updatedAt: new Date().toISOString() },
            ],
          },
        }),
      }),
    );
    await page.reload();
    await page.getByTestId('chat-fab').click();
    await expect(page.getByText('Tư vấn laptop')).toBeVisible({ timeout: 10_000 });
  });
});

test.describe('Chatbot — guest', () => {
  // Anonymous context — KHÔNG dùng storageState
  test.use({ storageState: { cookies: [], origins: [] } });

  test('guest sees login cta', async ({ page }) => {
    await page.goto('/');
    const cta = page.getByTestId('chat-cta-guest');
    await expect(cta).toBeVisible();
    await expect(cta).toHaveAttribute('href', /\/login\?next=/);
  });
});
