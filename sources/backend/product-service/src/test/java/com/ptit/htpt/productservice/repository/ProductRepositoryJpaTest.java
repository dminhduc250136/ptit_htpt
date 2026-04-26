package com.ptit.htpt.productservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.domain.ProductMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers @DataJpaTest cho ProductRepository.
 *
 * <p>Coverage:
 * 1. JPA save + findById round-trip
 * 2. Custom finder findBySlug (hit + miss)
 * 3. Soft-delete @SQLRestriction filter findAll khỏi deleted records
 * 4. ProductMapper.toDto boundary (DTO không có deleted field)
 *
 * <p>Container reuse + schema bootstrap đảm bảo Flyway V1 áp được trên schema product_svc.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryJpaTest {

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

  @Autowired
  private CategoryRepository categoryRepository;

  private void seedCategoryFor(String categoryId) {
    // Insert minimal category để FK constraint pass.
    // CategoryEntity factory tự gen UUID — ta cần id cụ thể, dùng JPA EntityManager via repo.
    // Vì categories có FK target từ products, seed trực tiếp qua repo.save sau khi tạo entity
    // với id tùy biến — dùng reflection hoặc đi qua native query.
    // Đơn giản hơn: dùng nativeQuery insert.
  }

  @Test
  void save_andFindById_returnsSameEntity() throws Exception {
    // seed category để FK pass
    insertCategory("cat-test-1", "Test Cat", "test-cat-1");

    ProductEntity entity = ProductEntity.create(
        "Sản phẩm test", "san-pham-test-" + System.nanoTime(),
        "cat-test-1", new BigDecimal("100000.00"), "ACTIVE");
    String id = entity.id();
    productRepository.save(entity);
    productRepository.flush();

    Optional<ProductEntity> found = productRepository.findById(id);
    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(id);
    assertThat(found.get().name()).isEqualTo("Sản phẩm test");
    assertThat(found.get().status()).isEqualTo("ACTIVE");
  }

  @Test
  void findBySlug_hitAndMiss() throws Exception {
    insertCategory("cat-test-2", "Test Cat 2", "test-cat-2");
    String slug = "unique-slug-" + System.nanoTime();
    ProductEntity entity = ProductEntity.create("X", slug, "cat-test-2",
        new BigDecimal("50000"), "ACTIVE");
    productRepository.save(entity);
    productRepository.flush();

    assertThat(productRepository.findBySlug(slug)).isPresent();
    assertThat(productRepository.findBySlug("does-not-exist-" + System.nanoTime())).isEmpty();
  }

  @Test
  void softDelete_filtersFromFindAll() throws Exception {
    insertCategory("cat-test-3", "Test Cat 3", "test-cat-3");
    ProductEntity p1 = ProductEntity.create("P1", "slug-p1-" + System.nanoTime(),
        "cat-test-3", new BigDecimal("100"), "ACTIVE");
    ProductEntity p2 = ProductEntity.create("P2", "slug-p2-" + System.nanoTime(),
        "cat-test-3", new BigDecimal("200"), "ACTIVE");
    productRepository.save(p1);
    productRepository.save(p2);
    productRepository.flush();

    long before = productRepository.findAll().stream()
        .filter(p -> "cat-test-3".equals(p.categoryId())).count();
    assertThat(before).isEqualTo(2);

    p1.softDelete();
    productRepository.save(p1);
    productRepository.flush();

    long after = productRepository.findAll().stream()
        .filter(p -> "cat-test-3".equals(p.categoryId())).count();
    assertThat(after).isEqualTo(1);
  }

  @Test
  void mapper_toDto_dropsDeletedField() throws Exception {
    insertCategory("cat-test-4", "Test Cat 4", "test-cat-4");
    ProductEntity entity = ProductEntity.create("Mapper test",
        "mapper-test-" + System.nanoTime(), "cat-test-4",
        new BigDecimal("1234.56"), "ACTIVE");
    productRepository.saveAndFlush(entity);

    var dto = ProductMapper.toDto(entity);
    assertThat(dto.id()).isEqualTo(entity.id());
    assertThat(dto.name()).isEqualTo("Mapper test");
    assertThat(dto.price()).isEqualByComparingTo("1234.56");

    // ProductDto record không có accessor `deleted()` — verify via reflection trên record components
    List<String> componentNames = java.util.Arrays.stream(dto.getClass().getRecordComponents())
        .map(java.lang.reflect.RecordComponent::getName).toList();
    assertThat(componentNames).doesNotContain("deleted");
  }

  /** Insert helper qua JDBC để control id (FK target). */
  private void insertCategory(String id, String name, String slug) throws Exception {
    try (Connection conn = postgres.createConnection("?currentSchema=product_svc");
         Statement stmt = conn.createStatement()) {
      stmt.execute("INSERT INTO product_svc.categories (id, name, slug, deleted, created_at, updated_at) "
          + "VALUES ('" + id + "', '" + name + "', '" + slug + "', FALSE, NOW(), NOW()) "
          + "ON CONFLICT (id) DO NOTHING");
    }
  }
}
