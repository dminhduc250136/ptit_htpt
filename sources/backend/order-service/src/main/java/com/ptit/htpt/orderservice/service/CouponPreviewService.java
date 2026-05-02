package com.ptit.htpt.orderservice.service;

import com.ptit.htpt.orderservice.domain.CouponEntity;
import com.ptit.htpt.orderservice.domain.CouponType;
import com.ptit.htpt.orderservice.exception.CouponErrorCode;
import com.ptit.htpt.orderservice.exception.CouponException;
import com.ptit.htpt.orderservice.repository.CouponRedemptionRepository;
import com.ptit.htpt.orderservice.repository.CouponRepository;
import com.ptit.htpt.orderservice.web.CouponDtos.CouponPreviewResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 20 / Plan 20-02 (D-08 step 1, D-04): Read-only validation cho preview
 * UX. KHÔNG mutate state — KHÔNG insert redemption, KHÔNG tăng usedCount.
 *
 * <p>Phân biệt 6 fail mode:
 * <ol>
 *   <li>NOT_FOUND — code không tồn tại
 *   <li>INACTIVE — admin disabled
 *   <li>EXPIRED — expiresAt &lt; now()
 *   <li>MIN_ORDER_NOT_MET — cartTotal &lt; minOrderAmount (kèm details.minOrderAmount)
 *   <li>MAX_USES_REACHED — usedCount &ge; maxTotalUses
 *   <li>ALREADY_REDEEMED — user đã redeem (UNIQUE constraint sẽ enforce ở step 2)
 * </ol>
 *
 * <p>Happy path: tính discount qua {@link #computeDiscount(CouponEntity, BigDecimal)}
 * (D-04: PERCENT chia 100 RoundingMode.FLOOR scale 0; FIXED dùng nguyên value;
 * cap discount ≤ cartTotal để finalTotal ≥ 0).
 *
 * <p>Plan 20-03 sẽ wire endpoint POST /orders/coupons/validate gọi service này.
 */
@Service
public class CouponPreviewService {

  private final CouponRepository couponRepository;
  private final CouponRedemptionRepository redemptionRepository;

  public CouponPreviewService(CouponRepository couponRepository,
                              CouponRedemptionRepository redemptionRepository) {
    this.couponRepository = couponRepository;
    this.redemptionRepository = redemptionRepository;
  }

  @Transactional(readOnly = true)
  public CouponPreviewResponse validate(String code, BigDecimal cartTotal, String userId) {
    CouponEntity c = couponRepository.findByCode(code)
        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

    if (!c.active()) {
      throw new CouponException(CouponErrorCode.COUPON_INACTIVE);
    }
    if (c.expiresAt() != null && c.expiresAt().isBefore(Instant.now())) {
      throw new CouponException(CouponErrorCode.COUPON_EXPIRED);
    }
    if (c.minOrderAmount() != null && cartTotal.compareTo(c.minOrderAmount()) < 0) {
      throw new CouponException(CouponErrorCode.COUPON_MIN_ORDER_NOT_MET,
          Map.of("minOrderAmount", c.minOrderAmount()));
    }
    if (c.maxTotalUses() != null && c.usedCount() >= c.maxTotalUses()) {
      throw new CouponException(CouponErrorCode.COUPON_MAX_USES_REACHED);
    }
    if (userId != null && redemptionRepository.existsByCouponIdAndUserId(c.id(), userId)) {
      throw new CouponException(CouponErrorCode.COUPON_ALREADY_REDEEMED);
    }

    BigDecimal discount = computeDiscount(c, cartTotal);
    BigDecimal finalTotal = cartTotal.subtract(discount);
    return new CouponPreviewResponse(
        c.code(),
        c.type().name(),
        c.value(),
        discount,
        finalTotal,
        "Áp dụng mã thành công"
    );
  }

  /**
   * D-04 discount math:
   * <ul>
   *   <li>PERCENT: cartTotal × value ÷ 100, RoundingMode.FLOOR, scale=0 (làm tròn xuống VND)
   *   <li>FIXED: trả nguyên value
   * </ul>
   * Cap discount ≤ cartTotal để finalTotal không âm.
   */
  public static BigDecimal computeDiscount(CouponEntity c, BigDecimal cartTotal) {
    BigDecimal raw;
    if (c.type() == CouponType.PERCENT) {
      raw = cartTotal.multiply(c.value())
          .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
    } else {
      raw = c.value();
    }
    return raw.min(cartTotal);
  }
}
