package com.ptit.htpt.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.orderservice.domain.CouponEntity;
import com.ptit.htpt.orderservice.domain.CouponType;
import com.ptit.htpt.orderservice.domain.OrderDto;
import com.ptit.htpt.orderservice.exception.CouponErrorCode;
import com.ptit.htpt.orderservice.exception.CouponException;
import com.ptit.htpt.orderservice.repository.CouponRedemptionRepository;
import com.ptit.htpt.orderservice.repository.CouponRepository;
import com.ptit.htpt.orderservice.repository.OrderRepository;
import com.ptit.htpt.orderservice.service.OrderCrudService.CreateOrderCommand;
import com.ptit.htpt.orderservice.service.OrderCrudService.OrderItemRequest;
import com.ptit.htpt.orderservice.service.OrderCrudService.ShippingAddressRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 20 / Plan 20-03 Task 2 — D-25 race condition tests cho atomic coupon redemption.
 *
 * <p>Test R1 (maxTotalUses=1, 2 user khác): UPDATE conditional rowsAffected → 1 thread thắng,
 * 1 thread fail COUPON_CONFLICT_OR_EXHAUSTED. usedCount=1 cuối cùng.
 *
 * <p>Test R2 (cùng userId, maxTotalUses=10): UNIQUE(coupon_id, user_id) violation
 * → 1 thread thắng, 1 thread fail COUPON_ALREADY_REDEEMED. Thread thua rollback toàn transaction
 * (bao gồm UPDATE used_count) → usedCount=1.
 *
 * <p>Bonus: backward compat (no coupon) + atomic rollback (invalid coupon → no order persisted) +
 * server-compute discount (D-10).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderCouponRaceConditionIT {

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
  }

  @Autowired OrderCrudService orderCrudService;
  @Autowired CouponRepository couponRepo;
  @Autowired CouponRedemptionRepository redemptionRepo;
  @Autowired OrderRepository orderRepo;

  @BeforeEach
  void setUp() {
    redemptionRepo.deleteAll();
    couponRepo.deleteAll();
    orderRepo.deleteAll();
  }

  private CreateOrderCommand buildCmd(String couponCode) {
    return new CreateOrderCommand(
        List.of(new OrderItemRequest("p1", "Product 1", 1, new BigDecimal("100000"))),
        new ShippingAddressRequest("street", "ward", "district", "city", "12345"),
        "COD", null, couponCode
    );
  }

  // ---------- D-25 Test R1: 2 thread parallel, maxTotalUses=1 ----------
  @Test
  void d25_test1_twoThreadsParallel_maxTotalUses1_onlyOneSucceeds() throws Exception {
    CouponEntity c = CouponEntity.create("RACE1", CouponType.PERCENT,
        new BigDecimal("10"), BigDecimal.ZERO, 1, null, true);
    couponRepo.saveAndFlush(c);

    CreateOrderCommand cmd = buildCmd("RACE1");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger();
    AtomicInteger conflictCount = new AtomicInteger();

    Runnable taskA = () -> {
      try {
        start.await();
        orderCrudService.createOrderFromCommand("user-A", cmd);
        successCount.incrementAndGet();
      } catch (CouponException ex) {
        if (ex.errorCode() == CouponErrorCode.COUPON_CONFLICT_OR_EXHAUSTED) {
          conflictCount.incrementAndGet();
        }
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    };
    Runnable taskB = () -> {
      try {
        start.await();
        orderCrudService.createOrderFromCommand("user-B", cmd);
        successCount.incrementAndGet();
      } catch (CouponException ex) {
        if (ex.errorCode() == CouponErrorCode.COUPON_CONFLICT_OR_EXHAUSTED) {
          conflictCount.incrementAndGet();
        }
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    };

    Future<?> f1 = pool.submit(taskA);
    Future<?> f2 = pool.submit(taskB);
    start.countDown();
    f1.get(15, TimeUnit.SECONDS);
    f2.get(15, TimeUnit.SECONDS);
    pool.shutdown();

    assertThat(successCount.get()).isEqualTo(1);
    assertThat(conflictCount.get()).isEqualTo(1);
    CouponEntity reloaded = couponRepo.findByCode("RACE1").orElseThrow();
    assertThat(reloaded.usedCount()).isEqualTo(1);
    // Chỉ 1 order với coupon_code này được persist (thread thua rollback)
    long ordersWithCoupon = orderRepo.findAll().stream()
        .filter(o -> "RACE1".equals(o.couponCode()))
        .count();
    assertThat(ordersWithCoupon).isEqualTo(1);
  }

  // ---------- D-25 Test R2: 2 thread cùng user → UNIQUE violation ----------
  @Test
  void d25_test2_twoThreadsSameUser_oneSucceedsOneAlreadyRedeemed() throws Exception {
    CouponEntity c = CouponEntity.create("RACE2", CouponType.PERCENT,
        new BigDecimal("10"), BigDecimal.ZERO, 10, null, true);
    couponRepo.saveAndFlush(c);

    CreateOrderCommand cmd = buildCmd("RACE2");
    String sameUser = "user-X";
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger();
    AtomicInteger alreadyCount = new AtomicInteger();
    AtomicInteger otherErrCount = new AtomicInteger();

    Runnable task = () -> {
      try {
        start.await();
        orderCrudService.createOrderFromCommand(sameUser, cmd);
        successCount.incrementAndGet();
      } catch (CouponException ex) {
        if (ex.errorCode() == CouponErrorCode.COUPON_ALREADY_REDEEMED) {
          alreadyCount.incrementAndGet();
        } else {
          otherErrCount.incrementAndGet();
        }
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    };

    Future<?> f1 = pool.submit(task);
    Future<?> f2 = pool.submit(task);
    start.countDown();
    f1.get(15, TimeUnit.SECONDS);
    f2.get(15, TimeUnit.SECONDS);
    pool.shutdown();

    assertThat(successCount.get()).isEqualTo(1);
    assertThat(alreadyCount.get()).isEqualTo(1);
    assertThat(otherErrCount.get()).isEqualTo(0);

    CouponEntity reloaded = couponRepo.findByCode("RACE2").orElseThrow();
    // Thread 2 rollback bao gồm UPDATE used_count → cuối cùng chỉ 1
    assertThat(reloaded.usedCount()).isEqualTo(1);
    assertThat(redemptionRepo.countByCouponId(c.id())).isEqualTo(1L);
  }

  // ---------- Backward compat: no coupon → tạo order như cũ ----------
  @Test
  void noCoupon_createsOrderWithDefaults() {
    CreateOrderCommand cmd = buildCmd(null);

    OrderDto dto = orderCrudService.createOrderFromCommand("user-N", cmd);

    assertThat(dto.couponCode()).isNull();
    assertThat(dto.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(dto.total()).isEqualByComparingTo("100000");
  }

  // ---------- Happy: coupon hợp lệ → discount + snapshot 2 field ----------
  @Test
  void couponHappy_appliesDiscountAndPersistsSnapshot() {
    CouponEntity c = CouponEntity.create("OK10", CouponType.PERCENT,
        new BigDecimal("10"), BigDecimal.ZERO, null, null, true);
    couponRepo.saveAndFlush(c);

    CreateOrderCommand cmd = buildCmd("OK10");
    OrderDto dto = orderCrudService.createOrderFromCommand("user-H", cmd);

    assertThat(dto.couponCode()).isEqualTo("OK10");
    assertThat(dto.discountAmount()).isEqualByComparingTo("10000"); // 10% × 100000
    assertThat(dto.total()).isEqualByComparingTo("90000");
    CouponEntity reloaded = couponRepo.findByCode("OK10").orElseThrow();
    assertThat(reloaded.usedCount()).isEqualTo(1);
    assertThat(redemptionRepo.countByCouponId(c.id())).isEqualTo(1L);
  }

  // ---------- Atomic rollback: unknown coupon → no order persisted ----------
  @Test
  void unknownCoupon_rollsBackOrderCreation() {
    long before = orderRepo.count();
    CreateOrderCommand cmd = buildCmd("DOESNOTEXIST");

    try {
      orderCrudService.createOrderFromCommand("user-R", cmd);
      throw new AssertionError("Expected CouponException");
    } catch (CouponException ex) {
      assertThat(ex.errorCode()).isEqualTo(CouponErrorCode.COUPON_CONFLICT_OR_EXHAUSTED);
    }

    assertThat(orderRepo.count()).isEqualTo(before);
  }

  // ---------- D-10: server compute discount, KHÔNG tin client ----------
  @Test
  void serverComputeDiscount_fromSubtotal_notFromClient() {
    CouponEntity c = CouponEntity.create("FIX50K", CouponType.FIXED,
        new BigDecimal("50000"), BigDecimal.ZERO, null, null, true);
    couponRepo.saveAndFlush(c);

    // Cart: 2 × 100000 = subtotal 200000 (server-side compute từ items)
    CreateOrderCommand cmd = new CreateOrderCommand(
        List.of(new OrderItemRequest("p1", "P1", 2, new BigDecimal("100000"))),
        new ShippingAddressRequest("s", "w", "d", "c", "z"),
        "COD", null, "FIX50K"
    );

    OrderDto dto = orderCrudService.createOrderFromCommand("user-S", cmd);

    // Server tính từ subtotal=200000 (KHÔNG dùng cartTotal client) → discount=50000, total=150000
    assertThat(dto.discountAmount()).isEqualByComparingTo("50000");
    assertThat(dto.total()).isEqualByComparingTo("150000");
  }
}
