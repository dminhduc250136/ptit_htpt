package com.ptit.htpt.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 20 / COUP-01 (D-02, D-07, D-08): Redemption record cho mỗi lần
 * 1 user dùng 1 coupon trên 1 order.
 *
 * <p>UNIQUE(coupon_id, user_id) enforce 1-mã/user ở DB level (D-08 atomic step 2).
 * order_id UNIQUE (column-level) chống double-insert nếu retry.
 *
 * <p>Append-only — KHÔNG soft-delete (D-07), KHÔNG rollback khi hủy order (D-07).
 */
@Entity
@Table(
    name = "coupon_redemptions",
    schema = "order_svc",
    uniqueConstraints = @UniqueConstraint(columnNames = {"coupon_id", "user_id"}))
public class CouponRedemptionEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "coupon_id", nullable = false)
  private CouponEntity coupon;

  @Column(name = "user_id", nullable = false, length = 36)
  private String userId;

  @Column(name = "order_id", nullable = false, length = 36, unique = true)
  private String orderId;

  @Column(name = "redeemed_at", nullable = false, updatable = false)
  private Instant redeemedAt;

  protected CouponRedemptionEntity() {}

  public static CouponRedemptionEntity create(CouponEntity coupon, String userId, String orderId) {
    CouponRedemptionEntity r = new CouponRedemptionEntity();
    r.id = UUID.randomUUID().toString();
    r.coupon = coupon;
    r.userId = userId;
    r.orderId = orderId;
    r.redeemedAt = Instant.now();
    return r;
  }

  public String id() { return id; }
  public CouponEntity coupon() { return coupon; }
  public String userId() { return userId; }
  public String orderId() { return orderId; }
  public Instant redeemedAt() { return redeemedAt; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CouponRedemptionEntity that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
