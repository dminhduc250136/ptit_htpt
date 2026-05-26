package com.ptit.htpt.orderservice.repository;

import com.ptit.htpt.orderservice.domain.CouponRedemptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Phase 20 / COUP-01 (D-08, D-14): Coupon redemption repository.
 *
 * <p>{@link #existsByCouponIdAndUserId(String, String)}: D-08 atomic step 2 —
 * defense-in-depth check trước insert (UNIQUE constraint là source of truth,
 * nhưng app-level check giúp throw COUPON_ALREADY_REDEEMED message rõ hơn
 * thay vì raw DataIntegrityViolationException).
 *
 * <p>{@link #countByCouponId(String)}: D-14 hard-DELETE guard — admin chỉ
 * xoá được coupon khi count=0 (chưa có ai redeem). Nếu &gt; 0 thì gợi ý
 * dùng disable (PATCH /active = false) thay vì delete.
 */
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemptionEntity, String> {

  /** D-08 atomic step 2: kiểm tra trước insert (defense-in-depth). */
  boolean existsByCouponIdAndUserId(String couponId, String userId);

  /** D-14 DELETE guard: chỉ hard-delete khi count=0. */
  long countByCouponId(String couponId);
}
