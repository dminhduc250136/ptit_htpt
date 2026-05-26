package com.ptit.htpt.orderservice.domain;

/**
 * Phase 20 / COUP-01 (D-03): Coupon type discriminator.
 * Map qua {@code @Enumerated(EnumType.STRING)} → DB CHECK constraint
 * {@code type IN ('PERCENT','FIXED')} validate ở layer DB (defense-in-depth).
 */
public enum CouponType {
  PERCENT,
  FIXED
}
