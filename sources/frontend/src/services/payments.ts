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

// ===== Phase 26 / PAY-02: VNPay return page resolve orderId =====

/** Shape trả về từ GET /api/payments/vnpay/return (Plan 26-01 Task 2 buildReturnView). */
export interface VNPayReturnResult {
  valid: boolean;
  orderId: string | null;
  responseCode: string | null;
  vnpTransactionNo: string | null;
}

/**
 * Resolve orderId từ VNPay redirect query string.
 *
 * Gọi `GET /api/payments/vnpay/return?{searchParams}` — forward TOÀN BỘ query string
 * VNPay redirect về. Endpoint public (gateway whitelist Plan 26-01 Task 3, không cần JWT).
 * Nguồn sự thật vẫn là IPN (D-06); endpoint này CHỈ xác minh chữ ký để lấy orderId
 * cho polling — KHÔNG cập nhật DB.
 *
 * @param searchParams — URLSearchParams từ window.location.search khi VNPay redirect về
 */
export function getVNPayReturn(searchParams: URLSearchParams): Promise<VNPayReturnResult> {
  const qs = searchParams.toString();
  return httpGet<VNPayReturnResult>(`/api/payments/vnpay/return${qs ? `?${qs}` : ''}`);
}
