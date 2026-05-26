package com.ptit.htpt.orderservice.service;

import com.ptit.htpt.orderservice.domain.CouponEntity;
import com.ptit.htpt.orderservice.domain.CouponRedemptionEntity;
import com.ptit.htpt.orderservice.exception.CouponErrorCode;
import com.ptit.htpt.orderservice.exception.CouponException;
import com.ptit.htpt.orderservice.repository.CouponRedemptionRepository;
import com.ptit.htpt.orderservice.repository.CouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Phase 20 / Plan 20-02 (D-08 step 2): Atomic redemption helper.
 *
 * <p>PHẢI gọi từ {@code @Transactional} cha (Plan 20-03 OrderCrudService.create)
 * để UPDATE coupons + INSERT redemption + INSERT order trong CÙNG transaction.
 * Nếu transaction rollback (ví dụ stock shortage downstream) thì usedCount
 * được rollback theo — KHÔNG cần compensating.
 *
 * <p>Race-safety qua atomic UPDATE conditional (D-08 source-of-truth):
 * {@code WHERE active=true AND (expires_at IS NULL OR expires_at > now())
 * AND (max_total_uses IS NULL OR used_count < max_total_uses)}.
 * Nếu rowsAffected=0 → throw {@link CouponErrorCode#COUPON_CONFLICT_OR_EXHAUSTED}.
 *
 * <p>UNIQUE(coupon_id, user_id) enforce 1-mã/user ở DB level — nếu user race-redeem
 * cùng coupon 2 lần parallel, INSERT thứ 2 throw DataIntegrityViolationException
 * → catch → throw {@link CouponErrorCode#COUPON_ALREADY_REDEEMED}.
 */
@Service
public class CouponRedemptionService {

  private static final Logger log = LoggerFactory.getLogger(CouponRedemptionService.class);

  private final CouponRepository couponRepository;
  private final CouponRedemptionRepository redemptionRepository;

  public CouponRedemptionService(CouponRepository couponRepository,
                                 CouponRedemptionRepository redemptionRepository) {
    this.couponRepository = couponRepository;
    this.redemptionRepository = redemptionRepository;
  }

  /**
   * D-08 step 2 atomic redeem. Trả CouponEntity reloaded sau UPDATE thành công
   * (caller cần code + computed discount + type + value để snapshot lên order).
   *
   * @throws CouponException COUPON_CONFLICT_OR_EXHAUSTED khi redeemAtomic returns 0
   * @throws CouponException COUPON_ALREADY_REDEEMED khi insert vi phạm UNIQUE(coupon_id,user_id)
   */
  public CouponEntity atomicRedeem(String code, String userId, String orderId) {
    int rows = couponRepository.redeemAtomic(code);
    if (rows == 0) {
      log.info("[D-08] redeemAtomic rowsAffected=0 (race-lose / expired / inactive / maxed): code={}",
          code);
      throw new CouponException(CouponErrorCode.COUPON_CONFLICT_OR_EXHAUSTED);
    }

    CouponEntity c = couponRepository.findByCode(code)
        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_CONFLICT_OR_EXHAUSTED));

    try {
      redemptionRepository.save(CouponRedemptionEntity.create(c, userId, orderId));
      // Force flush trước khi return để DataIntegrityViolation surface trong transaction này,
      // KHÔNG defer tới commit (caller cần catch ngay để rollback đúng).
      redemptionRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      log.info("[D-08] coupon already redeemed by user (UNIQUE violation): couponId={} userId={}",
          c.id(), userId);
      throw new CouponException(CouponErrorCode.COUPON_ALREADY_REDEEMED);
    }
    return c;
  }
}
