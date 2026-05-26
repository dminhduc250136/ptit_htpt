package com.ptit.htpt.orderservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 20 / Plan 20-01 Task 1 — Testcontainers Postgres test cho Flyway V5
 * migration + JPA entity (CouponEntity, CouponRedemptionEntity, OrderEntity extension).
 *
 * <p>Cover Test 1–7 (acceptance): migration apply, persist + load PERCENT/FIXED,
 * CHECK constraint, UNIQUE redemption, OrderEntity backward compat + new field set.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CouponEntityIT {

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

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private JdbcTemplate jdbc;

  // ---------- Test 1: Migration apply ----------
  @Test
  void v5MigrationApplied_creates2TablesAndAlters2OrderColumns() {
    Integer historyRow = jdbc.queryForObject(
        "SELECT COUNT(*) FROM order_svc.flyway_schema_history WHERE version = '5' AND success = true",
        Integer.class);
    assertThat(historyRow).isEqualTo(1);

    Integer couponsTable = jdbc.queryForObject(
        "SELECT COUNT(*) FROM information_schema.tables "
            + "WHERE table_schema = 'order_svc' AND table_name = 'coupons'",
        Integer.class);
    assertThat(couponsTable).isEqualTo(1);

    Integer redemptionsTable = jdbc.queryForObject(
        "SELECT COUNT(*) FROM information_schema.tables "
            + "WHERE table_schema = 'order_svc' AND table_name = 'coupon_redemptions'",
        Integer.class);
    assertThat(redemptionsTable).isEqualTo(1);

    Integer discountCol = jdbc.queryForObject(
        "SELECT COUNT(*) FROM information_schema.columns "
            + "WHERE table_schema = 'order_svc' AND table_name = 'orders' "
            + "AND column_name = 'discount_amount'",
        Integer.class);
    assertThat(discountCol).isEqualTo(1);

    Integer codeCol = jdbc.queryForObject(
        "SELECT COUNT(*) FROM information_schema.columns "
            + "WHERE table_schema = 'order_svc' AND table_name = 'orders' "
            + "AND column_name = 'coupon_code'",
        Integer.class);
    assertThat(codeCol).isEqualTo(1);
  }

  // ---------- Test 2: CouponEntity persist + load (PERCENT, nullable fields) ----------
  @Test
  void couponEntity_persistAndLoadPercentWithNullables() {
    CouponEntity c = CouponEntity.create("SALE10", CouponType.PERCENT,
        new BigDecimal("10"), null, null, null, true);
    em.persist(c);
    em.flush();
    em.clear();

    CouponEntity reloaded = em.find(CouponEntity.class, c.id());
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.code()).isEqualTo("SALE10");
    assertThat(reloaded.type()).isEqualTo(CouponType.PERCENT);
    assertThat(reloaded.value()).isEqualByComparingTo("10");
    assertThat(reloaded.minOrderAmount()).isEqualByComparingTo("0");
    assertThat(reloaded.maxTotalUses()).isNull();
    assertThat(reloaded.expiresAt()).isNull();
    assertThat(reloaded.usedCount()).isEqualTo(0);
    assertThat(reloaded.active()).isTrue();
  }

  // ---------- Test 3: CouponEntity FIXED with all fields ----------
  @Test
  void couponEntity_persistAndLoadFixedWithAllFields() {
    Instant expiry = Instant.now().plus(7, ChronoUnit.DAYS);
    CouponEntity c = CouponEntity.create("FIXED50K", CouponType.FIXED,
        new BigDecimal("50000"), new BigDecimal("100000"), 100, expiry, true);
    em.persist(c);
    em.flush();
    em.clear();

    CouponEntity reloaded = em.find(CouponEntity.class, c.id());
    assertThat(reloaded.type()).isEqualTo(CouponType.FIXED);
    assertThat(reloaded.value()).isEqualByComparingTo("50000");
    assertThat(reloaded.minOrderAmount()).isEqualByComparingTo("100000");
    assertThat(reloaded.maxTotalUses()).isEqualTo(100);
    assertThat(reloaded.expiresAt()).isNotNull();
  }

  // ---------- Test 4: CHECK constraint on type ----------
  @Test
  void couponInsert_withInvalidType_violatesCheckConstraint() {
    assertThatThrownBy(() ->
        jdbc.update(
            "INSERT INTO order_svc.coupons "
                + "(id, code, type, value, min_order_amount, used_count, active, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())",
            UUID.randomUUID().toString(), "BADCHK", "INVALID",
            new BigDecimal("10"), new BigDecimal("0"), 0, true)
    ).isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---------- Test 5: UNIQUE(coupon_id, user_id) on coupon_redemptions ----------
  @Test
  void couponRedemption_duplicateUserPerCoupon_violatesUniqueConstraint() {
    CouponEntity c = CouponEntity.create("UNIQ-TEST", CouponType.PERCENT,
        new BigDecimal("5"), null, null, null, true);
    em.persist(c);
    em.flush();

    CouponRedemptionEntity r1 = CouponRedemptionEntity.create(c, "user-1", "order-1");
    em.persist(r1);
    em.flush();

    CouponRedemptionEntity r2 = CouponRedemptionEntity.create(c, "user-1", "order-2");
    assertThatThrownBy(() -> {
      em.persist(r2);
      em.flush();
    }).isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---------- Test 6: OrderEntity backward compat ----------
  @Test
  void orderEntity_loadLegacyOrderWithoutCoupon_returnsDefaultDiscountAndNullCode() {
    // Insert raw order via JDBC bỏ qua discount_amount + coupon_code → DEFAULT 0 / NULL kick in
    String orderId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO order_svc.orders "
            + "(id, user_id, total, status, deleted, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, now(), now())",
        orderId, "user-legacy", new BigDecimal("250000"), "PENDING", false);

    em.clear();
    OrderEntity o = em.find(OrderEntity.class, orderId);
    assertThat(o).isNotNull();
    assertThat(o.discountAmount()).isEqualByComparingTo("0");
    assertThat(o.couponCode()).isNull();
  }

  // ---------- Test 7: OrderEntity new field set + reload ----------
  @Test
  void orderEntity_setDiscountAndCode_persistsAndReloads() {
    OrderEntity o = OrderEntity.create("user-new", new BigDecimal("100000"), "PENDING", null);
    o.setDiscountAmount(new BigDecimal("10000"));
    o.setCouponCode("SALE10");
    em.persist(o);
    em.flush();
    em.clear();

    OrderEntity reloaded = em.find(OrderEntity.class, o.id());
    assertThat(reloaded.discountAmount()).isEqualByComparingTo("10000");
    assertThat(reloaded.couponCode()).isEqualTo("SALE10");
  }

  // Smoke: list query works (sanity) — test infrastructure only
  @Test
  void couponEntity_findAllAfterPersist_returnsExpectedCount() {
    CouponEntity a = CouponEntity.create("LIST-A", CouponType.PERCENT,
        new BigDecimal("5"), null, null, null, true);
    em.persist(a);
    em.flush();
    @SuppressWarnings("unchecked")
    List<Object> rows = em.createNativeQuery(
        "SELECT id FROM order_svc.coupons WHERE code = 'LIST-A'").getResultList();
    assertThat(rows).hasSize(1);
  }
}
