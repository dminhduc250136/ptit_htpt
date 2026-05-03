package com.ptit.htpt.productservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.productservice.domain.ProductEntity;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * Phase 19 / Plan 03 — IT cho ProductRepository.findLowStock.
 *
 * <p>Coverage:
 * 1. findLowStock(10) trên seed [3,5,8,12,20] → trả [3,5,8] sorted ASC
 * 2. cap 50 áp dụng (seed 60 stock=1)
 * 3. soft-delete excluded (@SQLRestriction)
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryLowStockIT {

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
  static void init() throws Exception {
    postgres.start();
    try (Connection conn = postgres.createConnection("");
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE SCHEMA IF NOT EXISTS product_svc");
    }
  }

  @Autowired
  private ProductRepository productRepository;

  @BeforeEach
  void cleanSlate() throws Exception {
    productRepository.deleteAll();
    productRepository.flush();
    insertCategory("cat-low-stock", "Low Stock Cat", "low-stock-cat-" + System.nanoTime());
  }

  @Test
  void findLowStock_filtersAndSortsAsc() {
    saveWithStock("p-3", 3);
    saveWithStock("p-5", 5);
    saveWithStock("p-8", 8);
    saveWithStock("p-12", 12);
    saveWithStock("p-20", 20);

    List<ProductEntity> result = productRepository.findLowStock(10, PageRequest.of(0, 50));
    assertThat(result).hasSize(3);
    assertThat(result.stream().map(ProductEntity::stock).toList())
        .containsExactly(3, 5, 8);
  }

  @Test
  void findLowStock_capsAt50() {
    for (int i = 0; i < 60; i++) {
      saveWithStock("cap-" + i, 1);
    }
    List<ProductEntity> result = productRepository.findLowStock(10, PageRequest.of(0, 50));
    assertThat(result).hasSize(50);
  }

  @Test
  void findLowStock_excludesSoftDeleted() {
    ProductEntity active = saveWithStock("active-low", 1);
    ProductEntity deleted = saveWithStock("deleted-low", 1);
    deleted.softDelete();
    productRepository.save(deleted);
    productRepository.flush();

    List<ProductEntity> result = productRepository.findLowStock(10, PageRequest.of(0, 50));
    assertThat(result).extracting(ProductEntity::id).containsOnly(active.id());
  }

  private ProductEntity saveWithStock(String nameTag, int stock) {
    ProductEntity p = ProductEntity.create(
        "Product " + nameTag,
        "slug-" + nameTag + "-" + System.nanoTime(),
        "cat-low-stock",
        new BigDecimal("100.00"),
        "ACTIVE",
        "BrandX",
        "https://thumb.example/" + nameTag + ".webp",
        null,
        null);
    p.setStock(stock);
    productRepository.save(p);
    productRepository.flush();
    return p;
  }

  private void insertCategory(String id, String name, String slug) throws Exception {
    try (Connection conn = postgres.createConnection("?currentSchema=product_svc");
         Statement stmt = conn.createStatement()) {
      stmt.execute("INSERT INTO product_svc.categories (id, name, slug, deleted, created_at, updated_at) "
          + "VALUES ('" + id + "', '" + name + "', '" + slug + "', FALSE, NOW(), NOW()) "
          + "ON CONFLICT (id) DO NOTHING");
    }
  }
}
