package com.ptit.htpt.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.htpt.orderservice.domain.CouponEntity;
import com.ptit.htpt.orderservice.domain.CouponType;
import com.ptit.htpt.orderservice.exception.CouponErrorCode;
import com.ptit.htpt.orderservice.exception.CouponException;
import com.ptit.htpt.orderservice.repository.CouponRedemptionRepository;
import com.ptit.htpt.orderservice.repository.CouponRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 20 / Plan 20-02 Task 2 — Testcontainers Postgres test cho
 * {@link CouponRedemptionService}. Cover Test R1–R3 (happy + race-lose + already-redeemed).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CouponRedemptionService.class)
class CouponRedemptionServiceIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("tmdt")
      .withUsername("tmdt")
      .withPassword("tmdt")
      .withInitScript("test-init/01-schemas.sql");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url",
        () -> postgres.getJdbcUrl() + "?currentSchema=order_svc");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "order_svc");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.schemas", () -> "order_svc");
    registry.add("spring.flyway.default-schema", () -> "order_svc");
    registry.add("spring.flyway.baseline-on-migrate", () -> "false");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
  }

  @Autowired private CouponRedemptionService redemptionService;
  @Autowired private CouponRepository couponRepo;
  @Autowired private CouponRedemptionRepository redemptionRepo;
  @PersistenceContext private EntityManager em;

  private CouponEntity persistCoupon(String code, CouponType type, BigDecimal value,
                                     Integer maxUses, boolean active, int initialUsedCount) {
    CouponEntity c = CouponEntity.create(code, type, value, BigDecimal.ZERO,
        maxUses, null, active);
    if (initialUsedCount > 0) {
      try {
        var f = CouponEntity.class.getDeclaredField("usedCount");
        f.setAccessible(true);
        f.setInt(c, initialUsedCount);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    couponRepo.saveAndFlush(c);
    em.clear();
    return c;
  }

  // ---------- Test R1: atomicRedeem happy path ----------
  @Test
  void r1_atomicRedeem_happy() {
    persistCoupon("REDEEM-OK", CouponType.PERCENT, new BigDecimal("10"),
        null, true, 0);

    CouponEntity reloaded = redemptionService.atomicRedeem(
        "REDEEM-OK", "user-1", "order-1");

    assertThat(reloaded.code()).isEqualTo("REDEEM-OK");
    assertThat(reloaded.usedCount()).isEqualTo(1);
    assertThat(redemptionRepo.countByCouponId(reloaded.id())).isEqualTo(1);
  }

  // ---------- Test R2: atomicRedeem race-lose (max reached) ----------
  @Test
  void r2_atomicRedeem_raceLose_throwsConflictOrExhausted() {
    persistCoupon("MAXED-OUT", CouponType.PERCENT, new BigDecimal("10"),
        1, true, 1);

    assertThatThrownBy(() ->
        redemptionService.atomicRedeem("MAXED-OUT", "user-1", "order-2"))
        .isInstanceOf(CouponException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_CONFLICT_OR_EXHAUSTED);
  }

  // ---------- Test R3: atomicRedeem twice cùng user → ALREADY_REDEEMED ----------
  @Test
  void r3_atomicRedeem_alreadyRedeemed_uniqueViolation() {
    persistCoupon("DUP-USER", CouponType.PERCENT, new BigDecimal("10"),
        null, true, 0);

    redemptionService.atomicRedeem("DUP-USER", "user-1", "order-1");
    em.clear();

    assertThatThrownBy(() ->
        redemptionService.atomicRedeem("DUP-USER", "user-1", "order-2"))
        .isInstanceOf(CouponException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_ALREADY_REDEEMED);
  }

  // ---------- Test R4: inactive coupon → conflict ----------
  @Test
  void r4_atomicRedeem_inactive_throwsConflictOrExhausted() {
    persistCoupon("DISABLED", CouponType.PERCENT, new BigDecimal("10"),
        null, false, 0);

    assertThatThrownBy(() ->
        redemptionService.atomicRedeem("DISABLED", "user-1", "order-1"))
        .isInstanceOf(CouponException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_CONFLICT_OR_EXHAUSTED);
  }

  // ---------- Test R5: not found code → conflict ----------
  @Test
  void r5_atomicRedeem_unknownCode_throwsConflictOrExhausted() {
    assertThatThrownBy(() ->
        redemptionService.atomicRedeem("NOPE", "user-1", "order-1"))
        .isInstanceOf(CouponException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_CONFLICT_OR_EXHAUSTED);
  }
}
