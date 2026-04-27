package com.ptit.htpt.orderservice.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.orderservice.domain.OrderEntity;
import com.ptit.htpt.orderservice.domain.OrderItemEntity;
import com.ptit.htpt.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 13 / Plan 02: Integration test cho InternalOrderController.
 *
 * <p>Test 2 behavior chính:
 * 1. eligible=true khi user có DELIVERED order chứa productId.
 * 2. eligible=false khi không có dữ liệu phù hợp.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InternalOrderControllerTest {

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

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private OrderRepository orderRepository;

  @BeforeEach
  void seedDeliveredOrder() {
    // Seed 1 order DELIVERED của userId="u-test-1" chứa productId="p-test-1"
    OrderEntity order = OrderEntity.create("u-test-1", new BigDecimal("1500000.00"), "DELIVERED", null);
    OrderItemEntity item = OrderItemEntity.create(order, "p-test-1", "Laptop Test", 1,
        new BigDecimal("1500000.00"));
    order.addItem(item);
    orderRepository.saveAndFlush(order);
  }

  @Test
  void checkEligibility_whenDeliveredOrderExists_returnsTrue() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/internal/orders/eligibility?userId=u-test-1&productId=p-test-1",
        String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).contains("\"eligible\":true");
  }

  @Test
  void checkEligibility_whenNoDeliveredOrder_returnsFalse() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/internal/orders/eligibility?userId=u-no-orders&productId=p-anything",
        String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).contains("\"eligible\":false");
  }
}
