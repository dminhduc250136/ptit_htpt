package com.ptit.htpt.paymentservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.paymentservice.domain.PaymentTransactionEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class PaymentTransactionRepositoryJpaTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("tmdt")
      .withUsername("tmdt")
      .withPassword("tmdt")
      .withInitScript("init-test-schema.sql");

  @DynamicPropertySource
  static void registerSchema(DynamicPropertyRegistry registry) {
    registry.add("spring.flyway.schemas", () -> "payment_svc");
    registry.add("spring.flyway.default-schema", () -> "payment_svc");
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "payment_svc");
    registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO payment_svc");
  }

  @Autowired
  PaymentTransactionRepository repository;

  @Test
  void saveAndFindById_preservesAllFields() {
    PaymentTransactionEntity entity = PaymentTransactionEntity.create(
        "session-abc", "REF-001", new BigDecimal("199.99"),
        "VNPAY", "SUCCESS", "Payment OK"
    );
    repository.save(entity);

    Optional<PaymentTransactionEntity> loaded = repository.findById(entity.id());

    assertThat(loaded).isPresent();
    PaymentTransactionEntity got = loaded.get();
    assertThat(got.id()).isEqualTo(entity.id());
    assertThat(got.sessionId()).isEqualTo("session-abc");
    assertThat(got.reference()).isEqualTo("REF-001");
    assertThat(got.message()).isEqualTo("Payment OK");
    assertThat(got.amount()).isEqualByComparingTo("199.99");
    assertThat(got.method()).isEqualTo("VNPAY");
    assertThat(got.status()).isEqualTo("SUCCESS");
  }

  @Test
  void softDelete_excludesFromFindAll() {
    PaymentTransactionEntity a = PaymentTransactionEntity.create("s1", "R1", BigDecimal.TEN, "M", "OK", null);
    PaymentTransactionEntity b = PaymentTransactionEntity.create("s2", "R2", BigDecimal.ONE, "M", "OK", null);
    repository.save(a);
    repository.save(b);

    a.softDelete();
    repository.save(a);

    List<PaymentTransactionEntity> all = repository.findAll();
    assertThat(all).hasSize(1);
    assertThat(all.get(0).id()).isEqualTo(b.id());
  }

  @Test
  void findBySessionId_returnsMatching() {
    PaymentTransactionEntity entity = PaymentTransactionEntity.create(
        "session-xyz", "REF-XYZ", new BigDecimal("50.00"), "MOMO", "PENDING", null);
    repository.save(entity);

    Optional<PaymentTransactionEntity> found = repository.findBySessionId("session-xyz");

    assertThat(found).isPresent();
    assertThat(found.get().reference()).isEqualTo("REF-XYZ");
  }

  @Test
  void roundTrip_preservesSessionIdReferenceMessage() {
    // Cross-cutting note #4 PATTERNS: GIỮ field cũ sessionId/reference/message
    PaymentTransactionEntity entity = PaymentTransactionEntity.create(
        "sess-1", "REF-1", new BigDecimal("10.00"), "BANK", "FAILED",
        "Insufficient funds — please retry"
    );
    repository.save(entity);
    repository.flush();

    PaymentTransactionEntity loaded = repository.findById(entity.id()).orElseThrow();
    assertThat(loaded.sessionId()).isEqualTo("sess-1");
    assertThat(loaded.reference()).isEqualTo("REF-1");
    assertThat(loaded.message()).isEqualTo("Insufficient funds — please retry");
  }
}
