package com.ptit.htpt.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 20 / COUP-01 (D-02, D-03, D-05, D-06): Coupon JPA entity map vào
 * {@code order_svc.coupons}.
 *
 * <p>Field nullable: {@code expiresAt} (D-05 — null = không hết hạn),
 * {@code maxTotalUses} (D-06 — null = không giới hạn lượt dùng).
 *
 * <p>Atomic redemption mutate {@code usedCount} qua
 * {@link com.ptit.htpt.orderservice.repository.CouponRepository#redeemAtomic(String)}
 * (D-08), KHÔNG qua setter — race-safe.
 *
 * <p>KHÔNG soft-delete (D-07). Admin disable qua {@link #setActive(boolean)} (D-14).
 */
@Entity
@Table(name = "coupons", schema = "order_svc")
public class CouponEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(nullable = false, length = 64, unique = true)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CouponType type;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal value;

  @Column(name = "min_order_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal minOrderAmount = BigDecimal.ZERO;

  /** D-06: nullable wrapper Integer — null = không giới hạn. */
  @Column(name = "max_total_uses")
  private Integer maxTotalUses;

  @Column(name = "used_count", nullable = false)
  private int usedCount = 0;

  /** D-05: nullable Instant — null = không hết hạn. */
  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // JPA proxy: protected no-arg constructor
  protected CouponEntity() {}

  public static CouponEntity create(String code, CouponType type, BigDecimal value,
                                    BigDecimal minOrderAmount, Integer maxTotalUses,
                                    Instant expiresAt, boolean active) {
    CouponEntity c = new CouponEntity();
    c.id = UUID.randomUUID().toString();
    c.code = code;
    c.type = type;
    c.value = value;
    c.minOrderAmount = minOrderAmount == null ? BigDecimal.ZERO : minOrderAmount;
    c.maxTotalUses = maxTotalUses;
    c.usedCount = 0;
    c.expiresAt = expiresAt;
    c.active = active;
    Instant now = Instant.now();
    c.createdAt = now;
    c.updatedAt = now;
    return c;
  }

  public void update(String code, CouponType type, BigDecimal value, BigDecimal minOrderAmount,
                     Integer maxTotalUses, Instant expiresAt, boolean active) {
    this.code = code;
    this.type = type;
    this.value = value;
    this.minOrderAmount = minOrderAmount == null ? BigDecimal.ZERO : minOrderAmount;
    this.maxTotalUses = maxTotalUses;
    this.expiresAt = expiresAt;
    this.active = active;
    this.updatedAt = Instant.now();
  }

  public void setActive(boolean active) {
    this.active = active;
    this.updatedAt = Instant.now();
  }

  // record-style accessors (mirror OrderEntity convention)
  public String id() { return id; }
  public String code() { return code; }
  public CouponType type() { return type; }
  public BigDecimal value() { return value; }
  public BigDecimal minOrderAmount() { return minOrderAmount; }
  public Integer maxTotalUses() { return maxTotalUses; }
  public int usedCount() { return usedCount; }
  public Instant expiresAt() { return expiresAt; }
  public boolean active() { return active; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CouponEntity that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
