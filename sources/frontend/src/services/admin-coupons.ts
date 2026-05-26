/**
 * Admin coupon service — Phase 20 D-14 endpoints qua gateway /api/orders/admin/coupons.
 *
 * 5 fetchers tương ứng với 5 endpoint admin (D-14):
 *   - listAdminCoupons (GET /admin/coupons)
 *   - getAdminCoupon   (GET /admin/coupons/{id})
 *   - createCoupon     (POST /admin/coupons)
 *   - updateCoupon     (PUT /admin/coupons/{id})
 *   - toggleCouponActive (PATCH /admin/coupons/{id}/active)
 *   - deleteCoupon     (DELETE /admin/coupons/{id})
 *
 * Lưu ý: gateway pass-through JWT, BE JwtRoleGuard verify role ADMIN
 * (T-20-06-01). FE chỉ render từ response — KHÔNG client-side authz.
 */

import { httpGet, httpPost, httpPut, httpPatch, httpDelete } from './http';
import type { AdminCoupon, PaginatedResponse } from '@/types';

export interface CouponUpsertBody {
  code: string;
  type: 'PERCENT' | 'FIXED';
  value: number;
  minOrderAmount: number;
  maxTotalUses?: number | null;
  expiresAt?: string | null; // ISO8601
  active: boolean;
}

export interface ListAdminCouponsParams {
  page?: number;
  size?: number;
  sort?: string;
  q?: string;
  active?: boolean;
}

export function listAdminCoupons(
  params?: ListAdminCouponsParams,
): Promise<PaginatedResponse<AdminCoupon>> {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  if (params?.size !== undefined) qs.set('size', String(params.size));
  if (params?.sort) qs.set('sort', params.sort);
  if (params?.q) qs.set('q', params.q);
  if (params?.active !== undefined) qs.set('active', String(params.active));
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<PaginatedResponse<AdminCoupon>>(`/api/orders/admin/coupons${suffix}`);
}

export function getAdminCoupon(id: string): Promise<AdminCoupon> {
  return httpGet<AdminCoupon>(`/api/orders/admin/coupons/${encodeURIComponent(id)}`);
}

export function createCoupon(body: CouponUpsertBody): Promise<AdminCoupon> {
  return httpPost<AdminCoupon>('/api/orders/admin/coupons', body);
}

export function updateCoupon(id: string, body: CouponUpsertBody): Promise<AdminCoupon> {
  return httpPut<AdminCoupon>(`/api/orders/admin/coupons/${encodeURIComponent(id)}`, body);
}

export function toggleCouponActive(id: string, active: boolean): Promise<AdminCoupon> {
  return httpPatch<AdminCoupon>(
    `/api/orders/admin/coupons/${encodeURIComponent(id)}/active`,
    { active },
  );
}

export function deleteCoupon(id: string): Promise<void> {
  return httpDelete<void>(`/api/orders/admin/coupons/${encodeURIComponent(id)}`);
}
