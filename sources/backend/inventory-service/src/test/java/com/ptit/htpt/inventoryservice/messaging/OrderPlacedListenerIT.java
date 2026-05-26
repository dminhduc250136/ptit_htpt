package com.ptit.htpt.inventoryservice.messaging;

import com.ptit.htpt.inventoryservice.domain.InventoryEntity;
import com.ptit.htpt.inventoryservice.domain.StockLedgerEntity;
import com.ptit.htpt.inventoryservice.messaging.config.RabbitMQConfig;
import com.ptit.htpt.inventoryservice.messaging.event.OrderEventEnvelope;
import com.ptit.htpt.inventoryservice.repository.InventoryRepository;
import com.ptit.htpt.inventoryservice.repository.ProcessedEventRepository;
import com.ptit.htpt.inventoryservice.repository.StockLedgerRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 23 / Plan 23-06 — D-18: 4 scenario IT cho inventory consumer (LOCK — đầy đủ, không defer).
 *
 * <p>Tests:
 *  - happyPath_publishOrderPlaced_inventoryDecremented
 *  - idempotent_samePayloadTwice_decrementOnce
 *  - permanentException_routedToDLQ_noRetry (prod-missing → PermanentMessageException)
 *  - transientThenSuccess_retryThenAck (@SpyBean StockLedgerRepository throw 2 lần đầu → retry success)
 *
 * <p>Approach `transientThenSuccess`: dùng @SpyBean StockLedgerRepository.save → Mockito doAnswer
 * throw TransientDataAccessResourceException 2 lần đầu (wrap qua TransientMessageException trong
 * listener), Spring AMQP retry interceptor (override application-test.yml: 3 attempts × 100ms exp
 * backoff) → attempt 3 callRealMethod → message ACK + kho trừ đúng 1 lần.
 *
 * <p>Plan 23-06 acceptance: KHÔNG @Disabled bất kỳ scenario nào.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class OrderPlacedListenerIT {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("tmdt").withUsername("tmdt").withPassword("tmdt")
      .withInitScript("test-init/01-schemas.sql");

  @Container
  @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management");

  @Autowired RabbitTemplate rabbitTemplate;
  @Autowired InventoryRepository inventoryRepository;
  @Autowired ProcessedEventRepository processedEventRepository;
  @SpyBean StockLedgerRepository stockLedgerRepository;
  @Autowired AmqpAdmin amqpAdmin;

  @BeforeEach
  void cleanup() {
    Mockito.reset(stockLedgerRepository);
    stockLedgerRepository.deleteAll();
    processedEventRepository.deleteAll();
    inventoryRepository.deleteAll();
  }

  private OrderEventEnvelope buildEnvelope(String eventId, String traceId, String orderId,
                                            String productId, int qty) {
    return new OrderEventEnvelope(
        eventId, "OrderPlaced", Instant.now().toString(), traceId,
        new OrderEventEnvelope.OrderPlacedPayload(
            orderId, "user-1",
            List.of(new OrderEventEnvelope.Item(productId, qty, new BigDecimal("100"))),
            new BigDecimal("100").multiply(BigDecimal.valueOf(qty)),
            "VND"));
  }

  @Test
  void happyPath_publishOrderPlaced_inventoryDecremented() {
    inventoryRepository.save(InventoryEntity.create("prod-1", 10, 0));
    String eventId = UUID.randomUUID().toString();
    OrderEventEnvelope envelope = buildEnvelope(eventId, "trace-1", "order-1", "prod-1", 3);

    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE,
        RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, envelope);

    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      InventoryEntity refreshed = inventoryRepository.findByProductId("prod-1").orElseThrow();
      assertThat(refreshed.quantity()).isEqualTo(7);
      assertThat(processedEventRepository.findById(eventId)).isPresent();
      assertThat(stockLedgerRepository.findByEventId(eventId)).hasSize(1);
    });
  }

  @Test
  void idempotent_samePayloadTwice_decrementOnce() {
    inventoryRepository.save(InventoryEntity.create("prod-2", 10, 0));
    String eventId = UUID.randomUUID().toString();
    OrderEventEnvelope envelope = buildEnvelope(eventId, "trace-2", "order-2", "prod-2", 3);

    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE,
        RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, envelope);
    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE,
        RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, envelope);

    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      InventoryEntity refreshed = inventoryRepository.findByProductId("prod-2").orElseThrow();
      // CHỈ trừ 1 lần dù publish 2 lần — D-06 idempotency qua processed_events PK
      assertThat(refreshed.quantity()).isEqualTo(7);
      assertThat(stockLedgerRepository.findByEventId(eventId)).hasSize(1);
    });
  }

  @Test
  void permanentException_routedToDLQ_noRetry() {
    // Không có inventory cho prod-missing → InventoryCrudService.decrementForOrder throw
    // PermanentMessageException (extend AmqpRejectAndDontRequeueException) → DLQ ngay, 0 retry.
    String eventId = UUID.randomUUID().toString();
    OrderEventEnvelope envelope = buildEnvelope(eventId, "trace-3", "order-3", "prod-missing", 1);

    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE,
        RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, envelope);

    Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
      var props = amqpAdmin.getQueueProperties(RabbitMQConfig.DLQ);
      assertThat(props).as("DLQ properties available").isNotNull();
      Object count = props.get("QUEUE_MESSAGE_COUNT");
      assertThat(count).as("DLQ message count present").isNotNull();
      assertThat(((Number) count).intValue()).isGreaterThanOrEqualTo(1);
    });
  }

  @Test
  void transientThenSuccess_retryThenAck() {
    // D-18 scenario 4 (LOCK): TransientDataAccessResourceException 2 lần đầu, lần 3 success.
    // Spring AMQP retry (test profile override: 3 attempts × 100ms exp backoff) → attempt 3 pass.
    // @SpyBean StockLedgerRepository: 2 invocation đầu throw, sau đó callRealMethod.
    inventoryRepository.save(InventoryEntity.create("prod-4", 10, 0));
    String eventId = UUID.randomUUID().toString();
    OrderEventEnvelope envelope = buildEnvelope(eventId, "trace-4", "order-4", "prod-4", 3);

    AtomicInteger attempts = new AtomicInteger(0);
    Mockito.doAnswer(invocation -> {
      int n = attempts.incrementAndGet();
      if (n <= 2) {
        throw new TransientDataAccessResourceException(
            "Simulated transient DB failure attempt=" + n);
      }
      return invocation.callRealMethod();
    }).when(stockLedgerRepository).save(Mockito.any(StockLedgerEntity.class));

    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE,
        RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, envelope);

    Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
      // Final state: ACK, kho trừ đúng 1 lần (retry không nhân đôi vì processedEvents idempotency).
      InventoryEntity refreshed = inventoryRepository.findByProductId("prod-4").orElseThrow();
      assertThat(refreshed.quantity()).isEqualTo(7);
      assertThat(processedEventRepository.findById(eventId)).isPresent();
      assertThat(stockLedgerRepository.findByEventId(eventId)).hasSize(1);
      // Bằng chứng retry: save invoked >= 3 lần (2 fail + 1 success)
      assertThat(attempts.get()).isGreaterThanOrEqualTo(3);
    });
  }
}
