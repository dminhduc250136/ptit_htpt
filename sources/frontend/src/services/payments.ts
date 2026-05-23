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

// ===== Phase 26.1 / PAY-02: MoMo return page resolve orderId =====

/** Shape trả về từ GET /api/payments/momo/return (Plan 26.1-01 buildReturnView). */
export interface MomoReturnResult {
  valid: boolean;
  orderId: string | null;
  resultCode: string | null;
  paymentTransactionNo: string | null;
}

/**
 * Resolve orderId từ MoMo redirect query string.
 *
 * Gọi `GET /api/payments/momo/return?{searchParams}` — forward TOÀN BỘ query string
 * MoMo redirect về. Endpoint public (gateway whitelist Plan 26.1-01, không cần JWT).
 * Nguồn sự thật vẫn là IPN (T-26.1-04); endpoint này CHỈ xác minh chữ ký để lấy orderId
 * cho polling — KHÔNG cập nhật DB.
 *
 * @param searchParams — URLSearchParams từ window.location.search khi MoMo redirect về
 */
export function getMomoReturn(searchParams: URLSearchParams): Promise<MomoReturnResult> {
  const qs = searchParams.toString();
  return httpGet<MomoReturnResult>(`/api/payments/momo/return${qs ? `?${qs}` : ''}`);
}
