/**
 * Phase 22 / Plan 22-07 — Helper Playwright mock cho `POST /api/chat/stream`.
 *
 * Trả về deterministic text/event-stream body (mỗi event là 1 dòng JSON) để
 * E2E spec chạy được trong CI mà KHÔNG cần `ANTHROPIC_API_KEY` thực.
 *
 * Wire format khớp với route handler thực (Phase 22 Plan 05, D-14):
 *   { "type": "delta", "text": "..." }
 *   { "type": "done", "sessionId": N, "usage": {...} }
 *   { "type": "error", "error": "..." }
 */
import type { Page } from '@playwright/test';

export interface MockChatEvent {
  type: 'delta' | 'done' | 'error';
  text?: string;
  sessionId?: number;
  error?: string;
  usage?: Record<string, number>;
}

/** Intercept POST /api/chat/stream và trả deterministic SSE body. */
export async function mockChatStream(page: Page, events: MockChatEvent[]): Promise<void> {
  await page.route('**/api/chat/stream', async (route) => {
    if (route.request().method() !== 'POST') {
      return route.continue();
    }
    const body = events.map((e) => JSON.stringify(e)).join('\n') + '\n';
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream; charset=utf-8',
        'Cache-Control': 'no-cache, no-transform',
      },
      body,
    });
  });
}

/**
 * Convenience: build delta events từ một chuỗi `fullText`, split theo word boundary
 * để mô phỏng streaming token-by-token.
 */
export function buildDeltas(fullText: string, sessionId = 1): MockChatEvent[] {
  const tokens = fullText.match(/\S+\s*/g) ?? [fullText];
  const out: MockChatEvent[] = tokens.map((t) => ({ type: 'delta', text: t }));
  out.push({ type: 'done', sessionId });
  return out;
}
