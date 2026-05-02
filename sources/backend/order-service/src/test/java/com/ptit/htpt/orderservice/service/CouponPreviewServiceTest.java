package com.ptit.htpt.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.htpt.orderservice.domain.CouponEntity;
import com.ptit.htpt.orderservice.domain.CouponRedemptionEntity;
import com.ptit.htpt.orderservice.domain.CouponType;
import com.ptit.htpt.orderservice.exception.CouponErrorCode;
import com.ptit.htpt.orderservice.exception.CouponException;
import com.ptit.htpt.orderservice.repository.CouponRedemptionRepository;
import com.ptit.htpt.orderservice.repository.CouponRepository;
import com.ptit.htpt.orderservice.web.CouponDtos.CouponPreviewResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * {@link CouponPreviewService}. Cover Test P1–P11 (6 fail mode + happy paths
 * PERCENT/FIXED + cap + FLOOR rounding).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CouponPreviewService.class)
class CouponPreviewServiceTest {

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

  @Autowired private CouponPreviewService previewService;
  @Autowired private CouponRepository couponRepo;
  @Autowired private CouponRedemptionRepository redemptionRepo;
  @PersistenceContext private EntityManager em;

  // ---------- helpers ----------
  private CouponEntity persistCoupon(String code, CouponType type, BigDecimal value,
                                     BigDecimal minOrder, Integer maxUses,
                                     Instant expiresAt, boolean active, int initialUsedCount) {
    CouponEntity c = CouponEntity.create(code, type, value,
        minOrder == null ? BigDecimal.ZERO : minOrder,
        maxUses, expiresAt, active);
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

  // ---------- Test P1: NOT_FOUND ----------
  @Test
  void p1_notFound() {
    assertThatThrownBy(() ->
        previewService.validate("DOES-NOT-EXIST", new BigDecimal("100000"), "user-1"))
        .isInstanceOf(CouponException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_FOUND);
  }

  // ---------- Test P2: INACTIVE ----------
  @Test
  void p2_inactive() {
    persistCoupon("INACTIVE10", CouponType.PERCENT, new BigDecimal("10"),
        null, null, null, false, 0);

    assertThatThrownBy(() ->
        previewService.validate("INACTIVE10", new BigDecimal("100000"), "user-1"))
        .isInstanceOf(CouponException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_INACTIVE);
  }

  // ---------- Test P3: EXPIRED ----------
  @Test
  void p3_expired() {
    persistCoupon("EXPIRED10", CouponType.PERCENT, new BigDecimal("10"),
        null, null, Instant.now().minus(1, ChronoUnit.HOURS), true, 0);

    assertThatThrownBy(() ->
        previewService.validate("EXPIRED10", new BigDecimal("100000"), "user-1"))
        .isInstanceOf(CouponException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_EXPIRED);
  }

  // ---------- Test P4: MIN_ORDER_NOT_MET với details ----------
  @Test
  void p4_minOrderNotMet_carriesDetails() {
    persistCoupon("MINORDER", CouponType.PERCENT, new BigDecimal("10"),
        new BigDecimal("200000"), null, null, true, 0);

    assertThatThrownBy(() ->
        previewService.validate("MINORDER", new BigDecimal("100000"), "user-1"))
        .isInstanceOf(CouponException.class)
        .satisfies(t -> {
          CouponException ex = (CouponException) t;
          assertThat(ex.errorCode()).isEqualTo(CouponErrorCode.COUPON_MIN_ORDER_NOT_MET);
          assertThat(ex.details()).containsKey("minOrderAmount");
        });
  }

  // ---------- Test P5: MAX_USES_REACHED ----------
  @Test
  void p5_maxUsesReached() {
    persistCoupon("MAXED", CouponType.PERCENT, new BigDecimal("10"),
        null, 10, null, true, 10);

    assertThatThrownBy(() ->
        previewService.validate("MAXED", new BigDecimal("100000"), "user-1"))
        .isInstanceOf(CouponException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_MAX_USES_REACHED);
  }

  // ---------- Test P6: ALREADY_REDEEMED ----------
  @Test
  void p6_alreadyRedeemed() {
    CouponEntity c = persistCoupon("ALREADY", CouponType.PERCENT, new BigDecimal("10"),
        null, null, null, true, 0);
    // Persist redemption cho user-1
    redemptionRepo.saveAndFlush(CouponRedemptionEntity.create(c, "user-1", "order-1"));
    em.clear();

    assertThatThrownBy(() ->
        previewService.validate("ALREADY", new BigDecimal("100000"), "user-1"))
        .isInstanceOf(CouponException.class)
        .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_ALREADY_REDEEMED);
  }

  // ---------- Test P7: PERCENT happy path 10% on 1500000 = 150000 ----------
  @Test
  void p7_percentHappy() {
    persistCoupon("PCT10", CouponType.PERCENT, new BigDecimal("10"),
        null, null, null, true, 0);

    CouponPreviewResponse resp = previewService.validate(
        "PCT10", new BigDecimal("1500000"), "user-1");

    assertThat(resp.code()).isEqualTo("PCT10");
    assertThat(resp.type()).isEqualTo("PERCENT");
    assertThat(resp.discountAmount()).isEqualByComparingTo("150000");
    assertThat(resp.finalTotal()).isEqualByComparingTo("1350000");
  }

  // ---------- Test P8: PERCENT 100% cap → finalTotal=0 ----------
  @Test
  void p8_percent100_capsAtCartTotal() {
    persistCoupon("PCT100", CouponType.PERCENT, new BigDecimal("100"),
        null, null, null, true, 0);

    CouponPreviewResponse resp = previewService.validate(
        "PCT100", new BigDecimal("1000"), "user-1");

    assertThat(resp.discountAmount()).isEqualByComparingTo("1000");
    assertThat(resp.finalTotal()).isEqualByComparingTo("0");
  }

  // ---------- Test P9: FIXED happy path ----------
  @Test
  void p9_fixedHappy() {
    persistCoupon("FIX50K", CouponType.FIXED, new BigDecimal("50000"),
        null, null, null, true, 0);

    CouponPreviewResponse resp = previewService.validate(
        "FIX50K", new BigDecimal("1500000"), "user-1");

    assertThat(resp.discountAmount()).isEqualByComparingTo("50000");
    assertThat(resp.finalTotal()).isEqualByComparingTo("1450000");
  }

  // ---------- Test P10: FIXED cap (discount > cartTotal) ----------
  @Test
  void p10_fixedCapsAtCartTotal() {
    persistCoupon("FIXBIG", CouponType.FIXED, new BigDecimal("999999"),
        null, null, null, true, 0);

    CouponPreviewResponse resp = previewService.validate(
        "FIXBIG", new BigDecimal("100000"), "user-1");

    assertThat(resp.discountAmount()).isEqualByComparingTo("100000");
    assertThat(resp.finalTotal()).isEqualByComparingTo("0");
  }

  // ---------- Test P11: PERCENT FLOOR rounding (33% on 999 = 329.67 → 329) ----------
  @Test
  void p11_percentFloorRounding() {
    persistCoupon("PCT33", CouponType.PERCENT, new BigDecimal("33"),
        null, null, null, true, 0);

    CouponPreviewResponse resp = previewService.validate(
        "PCT33", new BigDecimal("999"), "user-1");

    // 999 * 33 / 100 = 329.67 → FLOOR scale=0 → 329
    assertThat(resp.discountAmount()).isEqualByComparingTo("329");
    assertThat(resp.finalTotal()).isEqualByComparingTo("670");
  }

  // ---------- Test P12: anonymous (userId=null) skips ALREADY_REDEEMED check ----------
  @Test
  void p12_anonymousUserSkipsRedemptionCheck() {
    persistCoupon("ANON10", CouponType.PERCENT, new BigDecimal("10"),
        null, null, null, true, 0);

    CouponPreviewResponse resp = previewService.validate(
        "ANON10", new BigDecimal("1000000"), null);

    assertThat(resp.discountAmount()).isEqualByComparingTo("100000");
  }
}
