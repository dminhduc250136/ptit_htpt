package com.ptit.htpt.inventoryservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.htpt.inventoryservice.domain.InventoryDto;
import com.ptit.htpt.inventoryservice.domain.InventoryEntity;
import com.ptit.htpt.inventoryservice.domain.InventoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * JPA layer test cho InventoryRepository — boots Postgres 16 qua Testcontainers, runs Flyway V1.
 *
 * <p>Cover 4 behaviors: save+findById, findByProductId, UNIQUE constraint product_id,
 * InventoryDto round-trip.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryRepositoryJpaTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("tmdt")
      .withUsername("tmdt")
      .withPassword("tmdt")
      .withInitScript("test-init/01-schemas.sql");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url",
        () -> postgres.getJdbcUrl() + "?currentSchema=inventory_svc");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "inventory_svc");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.schemas", () -> "inventory_svc");
    registry.add("spring.flyway.default-schema", () -> "inventory_svc");
    registry.add("spring.flyway.baseline-on-migrate", () -> "false");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
  }

  @Autowired
  private InventoryRepository inventoryRepository;

  @Test
  void save_then_findById_returnsEntity() {
    InventoryEntity entity = InventoryEntity.create("prod-001", 25, 0);
    InventoryEntity saved = inventoryRepository.save(entity);

    assertThat(inventoryRepository.findById(saved.id())).hasValueSatisfying(found -> {
      assertThat(found.id()).isEqualTo(saved.id());
      assertThat(found.productId()).isEqualTo("prod-001");
      assertThat(found.quantity()).isEqualTo(25);
      assertThat(found.reserved()).isEqualTo(0);
    });
  }

  @Test
  void findByProductId_returnsEntity() {
    inventoryRepository.save(InventoryEntity.create("prod-001", 10, 0));
    assertThat(inventoryRepository.findByProductId("prod-001")).isPresent();
    assertThat(inventoryRepository.findByProductId("prod-999")).isEmpty();
  }

  @Test
  void uniqueConstraint_onProductId_isEnforced() {
    inventoryRepository.save(InventoryEntity.create("prod-002", 5, 0));
    inventoryRepository.flush();

    assertThatThrownBy(() -> {
      inventoryRepository.save(InventoryEntity.create("prod-002", 7, 0));
      inventoryRepository.flush();
    }).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void inventoryDto_roundTrip_preservesAllFields() {
    InventoryEntity saved = inventoryRepository.save(InventoryEntity.create("prod-003", 50, 3));
    InventoryDto dto = InventoryMapper.toDto(saved);

    assertThat(dto.id()).isEqualTo(saved.id());
    assertThat(dto.productId()).isEqualTo("prod-003");
    assertThat(dto.quantity()).isEqualTo(50);
    assertThat(dto.reserved()).isEqualTo(3);
    assertThat(dto.createdAt()).isEqualTo(saved.createdAt());
    assertThat(dto.updatedAt()).isEqualTo(saved.updatedAt());
  }
}
