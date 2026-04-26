package com.ptit.htpt.inventoryservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.htpt.inventoryservice.domain.InventoryDto;
import com.ptit.htpt.inventoryservice.domain.InventoryEntity;
import com.ptit.htpt.inventoryservice.domain.InventoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

/**
 * JPA layer test cho InventoryRepository — uses external Postgres 16 (host port 55434, schema
 * {@code inventory_svc}). Container managed bởi outer test runner (KHÔNG Testcontainers vì
 * docker socket không reachable từ inside maven CI container trên Windows).
 *
 * <p>Cover 4 behaviors: save+findById, findByProductId, UNIQUE constraint product_id,
 * InventoryDto round-trip.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://host.docker.internal:55434/test?currentSchema=inventory_svc",
    "spring.datasource.username=test",
    "spring.datasource.password=test",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.properties.hibernate.default_schema=inventory_svc",
    "spring.flyway.enabled=true",
    "spring.flyway.schemas=inventory_svc",
    "spring.flyway.default-schema=inventory_svc",
    "spring.flyway.baseline-on-migrate=false",
    "spring.flyway.locations=classpath:db/migration",
    "spring.flyway.clean-disabled=false"
})
class InventoryRepositoryJpaTest {

  @Autowired
  private InventoryRepository inventoryRepository;

  @BeforeEach
  void clean() {
    inventoryRepository.deleteAllInBatch();
  }

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
