/**
 * admin-chat.ts — admin-only chat helpers.
 *
 * SEPARATE from `services/chat.ts` (customer-facing, owned by Plan 22-05) to
 * avoid same-wave write race during Phase 22 Wave 3 parallel plans (22-05/22-06).
 *
 * fetchSuggestReply: 1-shot POST to /api/admin/orders/[id]/suggest-reply
 * (Plan 22-04). Server enforces requireAdmin (T-22-05). Returns generated VN
 * reply text — admin reviews + edits + sends manually (D-07: NO auto-send).
 */

import { getAccessToken } from '@/services/token';

export async function fetchSuggestReply(
  orderId: string,
): Promise<{ text: string; orderId: string }> {
  const token = getAccessToken();
  const res = await fetch(
    `/api/admin/orders/${encodeURIComponent(orderId)}/suggest-reply`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    },
  );
  const env = await res.json().catch(() => ({}));
  if (!res.ok) {
    const code = env?.code ?? 'AI_FAILED';
    const message = env?.message ?? `HTTP ${res.status}`;
    throw new Error(`${code}: ${message}`);
  }
  return env.data as { text: string; orderId: string };
}
