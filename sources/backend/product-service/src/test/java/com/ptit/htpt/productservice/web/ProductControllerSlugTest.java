package com.ptit.htpt.productservice.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.productservice.domain.CategoryEntity;
import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.repository.CategoryRepository;
import com.ptit.htpt.productservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
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
 * UAT-style integration test cho GET /products/slug/{slug} envelope shape.
 *
 * <p>Phase 5 refactor: dùng JPA repos + Testcontainers thay vì InMemoryProductRepository.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductControllerSlugTest {

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

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private ProductRepository productRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  private static final String SEED_SLUG = "uat-test-slug-product";

  @BeforeEach
  void seedProduct() {
    // Seed a category first so toResponse can serialize category.name + category.slug.
    CategoryEntity category = CategoryEntity.create("Test Category", "test-category-" + System.nanoTime());
    categoryRepository.saveAndFlush(category);

    ProductEntity product = ProductEntity.create(
        "UAT Test Slug Product",
        SEED_SLUG + "-" + System.nanoTime(),
        category.id(),
        new BigDecimal("99000"),
        "ACTIVE", null, null, null, null);
    // Override slug để cố định cho test:
    productRepository.saveAndFlush(reseedSlug(product, category.id()));
  }

  /** Tạo lại entity với slug cố định bằng cách dùng factory + reflection-free path qua save. */
  private ProductEntity reseedSlug(ProductEntity orig, String categoryId) {
    // Đơn giản: tạo entity mới với slug cố định (xóa cũ nếu có).
    productRepository.findBySlug(SEED_SLUG).ifPresent(p -> {
      productRepository.delete(p);
      productRepository.flush();
    });
    return ProductEntity.create(
        "UAT Test Slug Product",
        SEED_SLUG,
        categoryId,
        new BigDecimal("99000"),
        "ACTIVE", null, null, null, null);
  }

  @Test
  void getProductBySlug_returns200_withRichShape() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/products/slug/" + SEED_SLUG, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("\"status\":200");
    assertThat(body).contains("\"message\":\"Product loaded\"");
    assertThat(body).doesNotContain("\"INTERNAL_ERROR\"");
    assertThat(body).doesNotContain("\"NOT_FOUND\"");
    assertThat(body).contains("\"category\":");
    assertThat(body).contains("\"name\":\"Test Category\"");
    assertThat(body).contains("\"thumbnailUrl\":");
    assertThat(body).contains("\"rating\":");
    assertThat(body).contains("\"reviewCount\":");
    assertThat(body).contains("\"tags\":");
    assertThat(body).contains("\"slug\":\"" + SEED_SLUG + "\"");
  }

  @Test
  void getProductBySlug_returns404_whenSlugNotFound() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/products/slug/no-such-slug-exists", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("\"code\":\"NOT_FOUND\"");
  }
}
