/**
 * Payment service API — thin wrapper.
 *
 * Per plan guidance: the MVP payment flow runs through order-service.createOrder
 * (D-14: single POST /api/orders carries the payment method). Dedicated payment
 * endpoints (/payments/sessions, /payments/transactions) are exposed by
 * payment-service for admin/tracking flows and will be wired by later phases.
 *
 * Source: 04-RESEARCH.md §Pattern 2; Pitfall 7 (hand-narrow against generated
 * paths as needed).
 */

// ===== PAYMENT SERVICE API =====

import type { paths as _PaymentsPaths } from '@/types/api/payments.generated';
import { httpGet } from './http';

export type _PathsSurface = _PaymentsPaths;

/** List payment sessions for the current user. Admin/tracking only — not used in MVP checkout. */
export function listMyPaymentSessions(): Promise<unknown> {
  return httpGet<unknown>(`/api/payments/payments/sessions`);
}

/** Reserved — payment flow currently runs via order-service.createOrder. */
