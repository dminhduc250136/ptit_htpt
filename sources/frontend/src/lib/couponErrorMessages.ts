/**
 * D-11: Map BE CouponErrorCode → Vietnamese message cho user toast.
 * Match verbatim CONTEXT.md D-11 message text.
 *
 * 8 codes:
 *   COUPON_NOT_FOUND, COUPON_INACTIVE, COUPON_EXPIRED,
 *   COUPON_MIN_ORDER_NOT_MET (details.minOrderAmount: number),
 *   COUPON_ALREADY_REDEEMED, COUPON_MAX_USES_REACHED,
 *   COUPON_CONFLICT_OR_EXHAUSTED, COUPON_HAS_REDEMPTIONS.
 */
export const couponErrorMessages: Record<string, string> = {
  COUPON_NOT_FOUND: 'Mã giảm giá không tồn tại',
  COUPON_INACTIVE: 'Mã giảm giá đã bị vô hiệu hoá',
  COUPON_EXPIRED: 'Mã giảm giá đã hết hạn',
  COUPON_MIN_ORDER_NOT_MET:
    'Đơn hàng không đạt giá trị tối thiểu để dùng mã này',
  COUPON_ALREADY_REDEEMED: 'Bạn đã sử dụng mã giảm giá này',
  COUPON_MAX_USES_REACHED: 'Mã giảm giá đã hết lượt sử dụng',
  COUPON_CONFLICT_OR_EXHAUSTED: 'Mã giảm giá không còn khả dụng',
  COUPON_HAS_REDEMPTIONS:
    'Mã giảm giá đã có người dùng — vui lòng tắt thay vì xoá',
};

/**
 * Format error message với details nếu có (D-11 minOrderAmount).
 * Trả null nếu code không phải COUPON_* — caller fallback toast generic.
 */
export function formatCouponError(
  code: string,
  details?: Record<string, unknown>,
): string | null {
  const base = couponErrorMessages[code];
  if (!base) return null;
  if (
    code === 'COUPON_MIN_ORDER_NOT_MET' &&
    details &&
    typeof details.minOrderAmount === 'number'
  ) {
    const formatted = new Intl.NumberFormat('vi-VN').format(
      details.minOrderAmount,
    );
    return `Đơn hàng tối thiểu ${formatted} đ để dùng mã này`;
  }
  return base;
}

export function isCouponError(code: string): boolean {
  return code.startsWith('COUPON_');
}
