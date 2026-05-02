package com.ptit.htpt.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ptit.htpt.productservice.domain.CategoryEntity;
import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.repository.CategoryRepository;
import com.ptit.htpt.productservice.repository.ProductRepository;
import com.ptit.htpt.productservice.repository.ReviewRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ReviewServiceTest {

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
    // Tạo category + product test
    CategoryEntity category = CategoryEntity.create("Test Cat", "test-cat-" + System.nanoTime());
    categoryRepo.save(category);

    product = ProductEntity.create(
        "Test Product", "test-product-" + System.nanoTime(),
        category.id(), new BigDecimal("100000.00"), "ACTIVE",
        null, null, null, null);
    productRepo.save(product);
  }

  private void mockEligibility(boolean eligible) {
    Map<String, Object> data = new HashMap<>();
    data.put("eligible", eligible);
    Map<String, Object> response = new HashMap<>();
    response.put("data", data);
    when(restTemplate.getForObject(
        anyString(),
        eq(Map.class),
        anyString(),
        anyString()))
      .thenReturn(response);
  }

  @Test
  void createReview_xssPayloadStripped() {
    mockEligibility(true);
    String payload = "<script>alert(1)</script>Hello<b>!</b>";

    Map<String, Object> result = reviewService.createReview(
        product.id(), "u-xss-1", "User One", 5, payload);

    assertThat((String) result.get("content")).doesNotContain("<script>");
    assertThat((String) result.get("content")).doesNotContain("<b>");
    assertThat((String) result.get("content")).contains("Hello");
  }

  @Test
  void createReview_notEligible_throws422() {
    mockEligibility(false);
    assertThatThrownBy(() -> reviewService.createReview(
            product.id(), "u-ineligible-1", "User Two", 4, "good"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
          assertThat(rse.getReason()).contains("REVIEW_NOT_ELIGIBLE");
        });
  }

  @Test
  void createReview_duplicate_throws409() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-dup-1", "User Three", 5, "first");

    assertThatThrownBy(() -> reviewService.createReview(
            product.id(), "u-dup-1", "User Three", 4, "second"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(rse.getReason()).contains("REVIEW_ALREADY_EXISTS");
        });
  }

  @Test
  void createReview_recomputesAvgRating() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-avg-1", "U4", 5, null);
    reviewService.createReview(product.id(), "u-avg-2", "U5", 3, null);

    ProductEntity updated = productRepo.findById(product.id()).orElseThrow();
    assertThat(updated.avgRating().doubleValue()).isEqualTo(4.0);
    assertThat(updated.reviewCount()).isEqualTo(2);
  }
}
