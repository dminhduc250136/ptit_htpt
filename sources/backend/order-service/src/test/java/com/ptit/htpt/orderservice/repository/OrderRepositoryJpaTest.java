package com.ptit.htpt.orderservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.orderservice.domain.OrderEntity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * JPA layer test cho OrderRepository — boots Postgres 16 qua Testcontainers, runs Flyway V1.
 *
 * <p>Cover 5 behaviors: save+findById, findByUserId, soft-delete filter, OrderDto no `deleted`,
 * `note` round-trip.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryJpaTest {

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
  private OrderRepository orderRepository;

  private static final String DEMO_USER_ID = "00000000-0000-0000-0000-000000000002";

  @Test
  void save_then_findById_returnsEntity() {
    OrderEntity order = OrderEntity.create(DEMO_USER_ID, new BigDecimal("100000.00"), "PENDING", "test note");
    OrderEntity saved = orderRepository.save(order);

    assertThat(orderRepository.findById(saved.id())).hasValueSatisfying(found -> {
      assertThat(found.id()).isEqualTo(saved.id());
      assertThat(found.userId()).isEqualTo(DEMO_USER_ID);
      assertThat(found.total()).isEqualByComparingTo("100000.00");
      assertThat(found.status()).isEqualTo("PENDING");
    });
  }

  @Test
  void findByUserId_returnsAllOrdersForUser() {
    orderRepository.save(OrderEntity.create(DEMO_USER_ID, new BigDecimal("50000.00"), "PENDING", null));
    orderRepository.save(OrderEntity.create(DEMO_USER_ID, new BigDecimal("75000.00"), "DELIVERED", null));
    orderRepository.save(OrderEntity.create("other-user", new BigDecimal("10000.00"), "PENDING", null));

    List<OrderEntity> demoOrders = orderRepository.findByUserId(DEMO_USER_ID);
    assertThat(demoOrders).hasSize(2);
    assertThat(demoOrders).allMatch(o -> o.userId().equals(DEMO_USER_ID));
  }

  @Test
  void softDelete_filtersDeletedRecords() {
    OrderEntity order1 = orderRepository.save(OrderEntity.create("u1", new BigDecimal("10.00"), "PENDING", null));
    OrderEntity order2 = orderRepository.save(OrderEntity.create("u1", new BigDecimal("20.00"), "PENDING", null));

    orderRepository.delete(order1);   // @SQLDelete soft

    List<OrderEntity> remaining = orderRepository.findAll();
    assertThat(remaining).extracting(OrderEntity::id).contains(order2.id()).doesNotContain(order1.id());
  }

  @Test
  void orderDto_doesNotExposeDeletedField() throws Exception {
    // Compile-time grep: ensure OrderDto record component doesn't include `deleted`
    var components = com.ptit.htpt.orderservice.domain.OrderDto.class.getRecordComponents();
    assertThat(components).extracting(rc -> rc.getName()).doesNotContain("deleted");
  }

  @Test
  void note_field_roundTrip() {
    OrderEntity order = OrderEntity.create(DEMO_USER_ID, new BigDecimal("8489000.00"), "DELIVERED", "Giao gấp");
    orderRepository.save(order);

    OrderEntity reloaded = orderRepository.findById(order.id()).orElseThrow();
    assertThat(reloaded.note()).isEqualTo("Giao gấp");
  }
}
