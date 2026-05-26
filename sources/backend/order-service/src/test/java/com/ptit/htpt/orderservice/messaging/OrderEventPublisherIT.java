package com.ptit.htpt.orderservice.messaging;

import com.ptit.htpt.orderservice.messaging.config.RabbitMQConfig;
import com.ptit.htpt.orderservice.messaging.event.OrderEventEnvelope;
import com.ptit.htpt.orderservice.messaging.publisher.OrderEventPublisher;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 23 / Plan 23-06 — D-03 publish-after-commit semantics IT.
 *
 * <p>Test 1 (rollback_doesNotPublish): publish bên trong transaction rồi setRollbackOnly →
 * afterCommit callback KHÔNG được gọi → queue depth KHÔNG thay đổi.
 *
 * <p>Test 2 (commit_publishesAfterCommit): publish bên trong transaction commit thành công →
 * afterCommit callback fire → message tới queue → depth tăng.
 *
 * <p>Mitigation T-23-09 phantom-event: chứng minh transaction rollback KHÔNG để lại event
 * mồ côi ngoài broker (gate for D-03).
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class OrderEventPublisherIT {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("tmdt").withUsername("tmdt").withPassword("tmdt")
      .withInitScript("test-init/01-schemas.sql");

  @Container
  @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management");

  @Autowired OrderEventPublisher publisher;
  @Autowired RabbitTemplate rabbitTemplate;
  @Autowired AmqpAdmin amqpAdmin;
  @Autowired PlatformTransactionManager txManager;

  private OrderEventEnvelope.OrderPlacedPayload payload(String orderId) {
    // Phase 27: OrderPlacedPayload + Item có thêm customerEmail / productName.
    return new OrderEventEnvelope.OrderPlacedPayload(
        orderId, "user-1", "test@example.com",
        List.of(new OrderEventEnvelope.Item("prod-1", "Test Product", 1, new BigDecimal("100"))),
        new BigDecimal("100"), "VND");
  }

  private int queueDepth(String queue) {
    Object count = amqpAdmin.getQueueProperties(queue).get("QUEUE_MESSAGE_COUNT");
    return count == null ? 0 : ((Number) count).intValue();
  }

  @Test
  void rollback_doesNotPublish() {
    int beforeInv = queueDepth(RabbitMQConfig.INVENTORY_QUEUE);
    int beforeNotif = queueDepth(RabbitMQConfig.NOTIFICATION_QUEUE);

    TransactionTemplate tx = new TransactionTemplate(txManager);
    tx.execute(status -> {
      publisher.publishOrderPlaced(payload("order-rollback"));
      status.setRollbackOnly();
      return null;
    });

    // Đợi 2s để chắc chắn không có message bay tới (afterCommit không fire khi rollback)
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    assertThat(queueDepth(RabbitMQConfig.INVENTORY_QUEUE))
        .as("inventory queue depth không tăng khi rollback")
        .isEqualTo(beforeInv);
    assertThat(queueDepth(RabbitMQConfig.NOTIFICATION_QUEUE))
        .as("notification queue depth không tăng khi rollback")
        .isEqualTo(beforeNotif);
  }

  @Test
  void commit_publishesAfterCommit() {
    int beforeInv = queueDepth(RabbitMQConfig.INVENTORY_QUEUE);
    int beforeNotif = queueDepth(RabbitMQConfig.NOTIFICATION_QUEUE);

    TransactionTemplate tx = new TransactionTemplate(txManager);
    tx.execute(status -> {
      publisher.publishOrderPlaced(payload("order-commit"));
      return null;
    });

    Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      assertThat(queueDepth(RabbitMQConfig.INVENTORY_QUEUE))
          .as("inventory queue nhận được event sau commit")
          .isGreaterThan(beforeInv);
      assertThat(queueDepth(RabbitMQConfig.NOTIFICATION_QUEUE))
          .as("notification queue nhận được event sau commit")
          .isGreaterThan(beforeNotif);
    });
  }
}
