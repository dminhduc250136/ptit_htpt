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
 * TDD integration test cho keyword search — D-02.
 *
 * <p>RED phase: test này sẽ fail vì ProductController chưa nhận keyword param.
 * Sau khi implement xong sẽ PASS (GREEN).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductKeywordSearchTest {

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

  @BeforeEach
  void seedProducts() {
    productRepository.deleteAll();
    categoryRepository.deleteAll();

    CategoryEntity category = CategoryEntity.create("Laptop", "laptop");
    categoryRepository.saveAndFlush(category);

    // Sản phẩm có "laptop" trong tên
    productRepository.saveAndFlush(
        ProductEntity.create("Dell Laptop XPS 13", "dell-laptop-xps-13",
            category.id(), new BigDecimal("25000000"), "ACTIVE",
            "Dell", null, null, new BigDecimal("27000000")));
    productRepository.saveAndFlush(
        ProductEntity.create("HP Laptop 15", "hp-laptop-15",
            category.id(), new BigDecimal("15000000"), "ACTIVE",
            "HP", null, null, new BigDecimal("16000000")));

    // Sản phẩm KHÔNG có "laptop" trong tên
    productRepository.saveAndFlush(
        ProductEntity.create("Apple MacBook Pro", "apple-macbook-pro",
            category.id(), new BigDecimal("40000000"), "ACTIVE",
            "Apple", null, null, new BigDecimal("42000000")));
  }

  @Test
  void listProducts_withKeyword_returnsOnlyMatchingProducts() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/products?keyword=laptop", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("Dell Laptop XPS 13");
    assertThat(body).contains("HP Laptop 15");
    assertThat(body).doesNotContain("Apple MacBook Pro");
  }

  @Test
  void listProducts_withKeyword_caseInsensitive() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/products?keyword=LAPTOP", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("Dell Laptop XPS 13");
    assertThat(body).contains("HP Laptop 15");
  }

  @Test
  void listProducts_withoutKeyword_returnsAllProducts() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/products", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("Dell Laptop XPS 13");
    assertThat(body).contains("HP Laptop 15");
    assertThat(body).contains("Apple MacBook Pro");
  }

  @Test
  void listProducts_withBlankKeyword_returnsAllProducts() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/products?keyword=", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("Dell Laptop XPS 13");
    assertThat(body).contains("Apple MacBook Pro");
  }

  @Test
  void listProducts_withNonExistentKeyword_returnsEmptyContent() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/products?keyword=XYZ_NOTEXIST_999", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).isNotNull();
    // content array phải rỗng
    assertThat(body).contains("\"totalElements\":0");
  }
}
