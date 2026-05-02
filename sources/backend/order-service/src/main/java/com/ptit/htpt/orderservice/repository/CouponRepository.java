package com.ptit.htpt.orderservice.repository;

import com.ptit.htpt.orderservice.domain.CouponEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Phase 20 / COUP-01 (D-08, D-09): Coupon repository.
 *
 * <p>Extends JpaRepository → free CRUD + findAll(Pageable) cho admin list (D-14).
 * Custom finder {@link #findByCode(String)} cho preview validate (D-08 step 1).
 *
 * <p>{@link #redeemAtomic(String)} là trái tim race-safety: 1 statement
 * conditional UPDATE → caller check rowsAffected==1 quyết định tiếp tục
 * hay throw COUPON_CONFLICT_OR_EXHAUSTED.
 */
public interface CouponRepository extends JpaRepository<CouponEntity, String> {

  /**
   * D-08 step 1 (preview) + D-09 (re-fetch by code, KHÔNG dùng id từ preview).
   * Case-sensitive match — DB column UNIQUE preserves case; FE auto-uppercase
   * input trước khi gửi.
   */
  Optional<CouponEntity> findByCode(String code);

  /**
   * D-08 atomic redemption UPDATE conditional. Race-safe: nếu rowsAffected=0
   * → coupon đã hết lượt / inactive / expired → caller throw COUPON_CONFLICT_OR_EXHAUSTED.
   *
   * <p>Native query để Postgres-level execution + match SQL chính xác CONTEXT.md
   * (D-08 source-of-truth race-safety). Parameterized via {@code @Param} chống SQL injection.
   *
   * @param code natural key UNIQUE
   * @return rowsAffected: 1 = redeem thành công, 0 = race-lose / expired / inactive / maxed
   */
  @Modifying
  @Query(value = """
      UPDATE order_svc.coupons
      SET used_count = used_count + 1, updated_at = now()
      WHERE code = :code
        AND active = true
        AND (expires_at IS NULL OR expires_at > now())
        AND (max_total_uses IS NULL OR used_count < max_total_uses)
      """, nativeQuery = true)
  int redeemAtomic(@Param("code") String code);
}
