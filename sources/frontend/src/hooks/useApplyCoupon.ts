/**
 * D-18: React Query mutation cho preview validation.
 * Caller (checkout/page.tsx) quản lý applied coupon state local + auto re-validate khi cart đổi.
 *
 * KHÔNG dùng useQueryClient invalidate vì preview là client-side state local
 * component, không phải global cache.
 */
import { useMutation } from '@tanstack/react-query';
import {
  validateCoupon,
  type CouponValidateBody,
} from '@/services/coupons';
import type { CouponPreview } from '@/types';

export function useApplyCoupon(userId?: string) {
  return useMutation<CouponPreview, Error, CouponValidateBody>({
    mutationFn: (body: CouponValidateBody) => validateCoupon(body, userId),
  });
}
