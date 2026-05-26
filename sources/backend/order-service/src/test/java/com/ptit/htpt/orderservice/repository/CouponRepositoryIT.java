package com.ptit.htpt.orderservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.orderservice.domain.CouponEntity;
import com.ptit.htpt.orderservice.domain.CouponRedemptionEntity;
import com.ptit.htpt.orderservice.domain.CouponType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 20 / Plan 20-01 Task 2 — Testcontainers Postgres test cho
 * {@link CouponRepository} (findByCode + redeemAtomic D-08) +
 * {@link CouponRedemptionRepository} (existsBy + countBy D-08/D-14).
 *
 * <p>Cover Test 1–10 (acceptance).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CouponRepositoryIT {

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

  @Autowired
  private CouponRepository couponRepo;

  @Autowired
  private CouponRedemptionRepository redemptionRepo;

  @PersistenceContext
  private EntityManager em;

  // ---------- helpers ----------
  private CouponEntity persistCoupon(String code, CouponType type, BigDecimal value,
                                      Integer maxTotalUses, Instant expiresAt,
                                      boolean active, int initialUsedCount) {
    CouponEntity c = CouponEntity.create(code, type, value, BigDecimal.ZERO,
        maxTotalUses, expiresAt, active);
    if (initialUsedCount > 0) {
      // dùng reflection để set used_count khởi tạo (test fixture)
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

  // ---------- Test 1: findByCode happy path + miss ----------
  @Test
  void findByCode_existingAndMissing() {
    persistCoupon("SALE10", CouponType.PERCENT, new BigDecimal("10"), null, null, true, 0);

    Optional<CouponEntity> hit = couponRepo.findByCode("SALE10");
    assertThat(hit).isPresent();
    assertThat(hit.get().code()).isEqualTo("SALE10");

    Optional<CouponEntity> miss = couponRepo.findByCode("UNKNOWN");
    assertThat(miss).isEmpty();
  }

  // ---------- Test 2: findByCode case-sensitive ----------
  @Test
  void findByCode_caseSensitive() {
    persistCoupon("SALE10", CouponType.PERCENT, new BigDecimal("10"), null, null, true, 0);

    Optional<CouponEntity> lower = couponRepo.findByCode("sale10");
    assertThat(lower).isEmpty();
  }

  // ---------- Test 3: redeemAtomic happy path ----------
  @Test
  void redeemAtomic_happyPath_incrementsUsedCount() {
    CouponEntity c = persistCoupon("ATOMIC-OK", CouponType.PERCENT,
        new BigDecimal("10"), null, null, true, 0);

    int rows = couponRepo.redeemAtomic("ATOMIC-OK");
    assertThat(rows).isEqualTo(1);

    em.clear();
    CouponEntity reloaded = couponRepo.findById(c.id()).orElseThrow();
    assertThat(reloaded.usedCount()).isEqualTo(1);
  }

  // ---------- Test 4: redeemAtomic max reached ----------
  @Test
  void redeemAtomic_maxReached_returnsZero() {
    CouponEntity c = persistCoupon("MAXED", CouponType.FIXED,
        new BigDecimal("50000"), 1, null, true, 1);

    int rows = couponRepo.redeemAtomic("MAXED");
    assertThat(rows).isEqualTo(0);

    em.clear();
    CouponEntity reloaded = couponRepo.findById(c.id()).orElseThrow();
    assertThat(reloaded.usedCount()).isEqualTo(1);
  }

  // ---------- Test 5: redeemAtomic expired ----------
  @Test
  void redeemAtomic_expired_returnsZero() {
    persistCoupon("EXPIRED", CouponType.PERCENT, new BigDecimal("10"),
        null, Instant.now().minus(1, ChronoUnit.DAYS), true, 0);

    int rows = couponRepo.redeemAtomic("EXPIRED");
    assertThat(rows).isEqualTo(0);
  }

  // ---------- Test 6: redeemAtomic inactive ----------
  @Test
  void redeemAtomic_inactive_returnsZero() {
    persistCoupon("INACTIVE", CouponType.PERCENT, new BigDecimal("10"),
        null, null, false, 0);

    int rows = couponRepo.redeemAtomic("INACTIVE");
    assertThat(rows).isEqualTo(0);
  }

  // ---------- Test 7: redeemAtomic null limits ----------
  @Test
  void redeemAtomic_nullLimits_succeedsRegardlessOfUsedCount() {
    persistCoupon("UNCAPPED", CouponType.PERCENT, new BigDecimal("5"),
        null, null, true, 999);

    int rows = couponRepo.redeemAtomic("UNCAPPED");
    assertThat(rows).isEqualTo(1);
  }

  // ---------- Test 8: findAll baseline ----------
  @Test
  void findAll_returnsAllCoupons() {
    persistCoupon("A", CouponType.PERCENT, new BigDecimal("10"), null, null, true, 0);
    persistCoupon("B", CouponType.PERCENT, new BigDecimal("10"), null, null, true, 0);
    persistCoupon("C", CouponType.PERCENT, new BigDecimal("10"), null, null, true, 0);
    persistCoupon("D-INACTIVE", CouponType.FIXED, new BigDecimal("1000"), null, null, false, 0);
    persistCoupon("E-INACTIVE", CouponType.FIXED, new BigDecimal("1000"), null, null, false, 0);

    long total = couponRepo.count();
    assertThat(total).isEqualTo(5);
  }

  // ---------- Test 9: existsByCouponIdAndUserId ----------
  @Test
  void redemptionRepo_existsByCouponIdAndUserId() {
    CouponEntity c = persistCoupon("EXISTS-TEST", CouponType.PERCENT,
        new BigDecimal("10"), null, null, true, 0);

    CouponRedemptionEntity r = CouponRedemptionEntity.create(c, "user-1", "order-1");
    redemptionRepo.saveAndFlush(r);
    em.clear();

    assertThat(redemptionRepo.existsByCouponIdAndUserId(c.id(), "user-1")).isTrue();
    assertThat(redemptionRepo.existsByCouponIdAndUserId(c.id(), "user-other")).isFalse();
  }

  // ---------- Test 10: countByCouponId ----------
  @Test
  void redemptionRepo_countByCouponId() {
    CouponEntity target = persistCoupon("COUNT-TARGET", CouponType.PERCENT,
        new BigDecimal("5"), null, null, true, 0);
    CouponEntity other = persistCoupon("COUNT-OTHER", CouponType.PERCENT,
        new BigDecimal("5"), null, null, true, 0);

    redemptionRepo.saveAndFlush(CouponRedemptionEntity.create(target, "u-1", "o-1"));
    redemptionRepo.saveAndFlush(CouponRedemptionEntity.create(target, "u-2", "o-2"));
    redemptionRepo.saveAndFlush(CouponRedemptionEntity.create(target, "u-3", "o-3"));
    redemptionRepo.saveAndFlush(CouponRedemptionEntity.create(other, "u-1", "o-4"));
    redemptionRepo.saveAndFlush(CouponRedemptionEntity.create(other, "u-2", "o-5"));
    em.clear();

    assertThat(redemptionRepo.countByCouponId(target.id())).isEqualTo(3L);
    assertThat(redemptionRepo.countByCouponId(other.id())).isEqualTo(2L);
  }
}
