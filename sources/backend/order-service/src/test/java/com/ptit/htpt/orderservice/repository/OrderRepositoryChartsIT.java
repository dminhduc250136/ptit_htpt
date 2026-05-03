package com.ptit.htpt.orderservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.orderservice.domain.OrderEntity;
import com.ptit.htpt.orderservice.domain.OrderItemEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 19 / Plan 19-01 Task 1 — Testcontainers Postgres test cho 3 admin chart
 * aggregation queries trên {@link OrderRepository}.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryChartsIT {

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
  private OrderRepository repo;

  private OrderEntity newOrder(String status, BigDecimal total, Instant createdAt) {
    OrderEntity o = OrderEntity.create("u-test", total, status, null);
    // Override createdAt via reflection (test-only) — entity hardcodes Instant.now() in create()
    try {
      var f = OrderEntity.class.getDeclaredField("createdAt");
      f.setAccessible(true);
      f.set(o, createdAt);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return o;
  }

  @Test
  void aggregateRevenueByDay_excludesNonDeliveredAndAggregatesCorrectly() {
    Instant now = Instant.now();
    // 3 DELIVERED orders trên 3 ngày khác nhau
    OrderEntity d1 = repo.saveAndFlush(newOrder("DELIVERED", new BigDecimal("100.00"),
        now.minus(2, ChronoUnit.DAYS)));
    OrderEntity d2 = repo.saveAndFlush(newOrder("DELIVERED", new BigDecimal("200.00"),
        now.minus(1, ChronoUnit.DAYS)));
    OrderEntity d3 = repo.saveAndFlush(newOrder("DELIVERED", new BigDecimal("300.00"), now));
    // 1 PENDING order — phải bị loại
    repo.saveAndFlush(newOrder("PENDING", new BigDecimal("999.00"), now));

    List<Object[]> rows = repo.aggregateRevenueByDay(null);

    assertThat(rows).hasSize(3);
    BigDecimal sum = rows.stream()
        .map(r -> (BigDecimal) r[1])
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sum).isEqualByComparingTo("600.00");
  }

  @Test
  void aggregateTopProducts_returnsTop10SortedByQtyDesc() {
    Instant now = Instant.now();
    // Tạo 12 productIds với qty descending
    for (int i = 0; i < 12; i++) {
      OrderEntity o = newOrder("DELIVERED", new BigDecimal("100.00"), now);
      OrderItemEntity item = OrderItemEntity.create(o, "p-" + i, "Product " + i,
          12 - i, new BigDecimal("100.00"));
      o.addItem(item);
      repo.saveAndFlush(o);
    }

    List<Object[]> rows = repo.aggregateTopProducts(null, PageRequest.of(0, 10));

    assertThat(rows).hasSize(10);
    long firstQty = ((Number) rows.get(0)[1]).longValue();
    long lastQty = ((Number) rows.get(9)[1]).longValue();
    assertThat(firstQty).isGreaterThanOrEqualTo(lastQty);
    assertThat(firstQty).isEqualTo(12L);
  }

  @Test
  void aggregateStatusDistribution_returnsCountsPerStatus() {
    Instant now = Instant.now();
    repo.saveAndFlush(newOrder("PENDING", new BigDecimal("10.00"), now));
    repo.saveAndFlush(newOrder("PENDING", new BigDecimal("10.00"), now));
    repo.saveAndFlush(newOrder("DELIVERED", new BigDecimal("10.00"), now));
    repo.saveAndFlush(newOrder("CANCELLED", new BigDecimal("10.00"), now));

    List<Object[]> rows = repo.aggregateStatusDistribution();

    Map<String, Long> counts = new HashMap<>();
    for (Object[] r : rows) {
      counts.put((String) r[0], ((Number) r[1]).longValue());
    }
    assertThat(counts.get("PENDING")).isEqualTo(2L);
    assertThat(counts.get("DELIVERED")).isEqualTo(1L);
    assertThat(counts.get("CANCELLED")).isEqualTo(1L);
  }
}
