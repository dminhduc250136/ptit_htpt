package com.ptit.htpt.productservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.htpt.productservice.domain.CategoryEntity;
import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.domain.ReviewEntity;
import com.ptit.htpt.productservice.repository.CategoryRepository;
import com.ptit.htpt.productservice.repository.ProductRepository;
import com.ptit.htpt.productservice.repository.ReviewRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 21 / Plan 02 — kiểm tra cửa sổ chỉnh sửa hết hạn (T-21-02-03).
 * Override {@code app.reviews.edit-window-hours=0} → mọi edit đều quá hạn ngay tức thì.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "app.reviews.edit-window-hours=0")
class ReviewServiceEditWindowTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url",
        () -> postgres.getJdbcUrl() + "?currentSchema=product_svc");
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.schemas", () -> "product_svc");
    r.add("spring.flyway.default-schema", () -> "product_svc");
    r.add("spring.jpa.properties.hibernate.default_schema", () -> "product_svc");
  }

  @BeforeAll
  static void initSchema() throws Exception {
    postgres.start();
    try (Connection conn = postgres.createConnection("");
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE SCHEMA IF NOT EXISTS product_svc");
    }
  }

  @Autowired ReviewService reviewService;
  @Autowired ReviewRepository reviewRepo;
  @Autowired ProductRepository productRepo;
  @Autowired CategoryRepository categoryRepo;
  @MockBean RestTemplate restTemplate;

  ProductEntity product;

  @BeforeEach
  void setup() {
    reviewRepo.deleteAll();
    CategoryEntity category = CategoryEntity.create("Test Cat W", "test-cat-w-" + System.nanoTime());
    categoryRepo.save(category);
    product = ProductEntity.create(
        "Product W", "product-w-" + System.nanoTime(),
        category.id(), new BigDecimal("100000.00"), "ACTIVE",
        null, null, null, null);
    productRepo.save(product);
  }

  @Test
  void editReview_pastWindow_returns422() {
    ReviewEntity r = ReviewEntity.create(product.id(), "u-w-1", "U", 4, "old");
    reviewRepo.save(r);
    String rid = r.id();

    assertThatThrownBy(() -> reviewService.editReview(rid, "u-w-1", 5, "new"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          Assertions.assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
          Assertions.assertThat(rse.getReason()).contains("REVIEW_EDIT_WINDOW_EXPIRED");
        });
  }
}
