package com.ptit.htpt.notificationservice.messaging;

import com.ptit.htpt.notificationservice.messaging.config.RabbitMQConfig;
import com.ptit.htpt.notificationservice.messaging.event.OrderEventEnvelope;
import com.ptit.htpt.notificationservice.repository.DispatchLogRepository;
import com.ptit.htpt.notificationservice.repository.ProcessedEventRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 23 / Plan 23-06 — IT cho notification consumer.
 *
 * <p>3 scenario: happyPath, idempotent, topology smoke. KHÔNG có scenario permanent-to-DLQ
 * cho notification vì `recordOrderConfirmation` chấp nhận MỌI payload OrderPlaced (chỉ là
 * StringBuilder concat + INSERT dispatch_log) — KHÔNG có business validation throw
 * PermanentMessageException dựa trên payload. Defer DLQ verification cho inventory IT.
 *
 * <p>Approach: copy pattern @ServiceConnection của inventory IT — Testcontainers Postgres +
 * RabbitMQ provision tự động, Flyway init notification_svc schema + dispatch_log/processed_events.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class OrderPlacedNotifyListenerIT {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("tmdt").withUsername("tmdt").withPassword("tmdt")
      .withInitScript("test-init/01-schemas.sql");

  @Container
  @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management");

  @Autowired RabbitTemplate rabbitTemplate;
  @Autowired DispatchLogRepository dispatchLogRepository;
  @Autowired ProcessedEventRepository processedEventRepository;

  @BeforeEach
  void cleanup() {
    dispatchLogRepository.deleteAll();
    processedEventRepository.deleteAll();
  }

  private OrderEventEnvelope buildEnvelope(String eventId, String traceId, String orderId, String userId) {
    return new OrderEventEnvelope(
        eventId, "OrderPlaced", Instant.now().toString(), traceId,
        new OrderEventEnvelope.OrderPlacedPayload(
            orderId, userId, "customer@example.com",
            List.of(new OrderEventEnvelope.Item("prod-1", "Product 1", 1, new BigDecimal("100"))),
            new BigDecimal("100"), "VND"));
  }

  @Test
  void happyPath_consumeOrderPlaced_dispatchLogCreated() {
    String eventId = UUID.randomUUID().toString();
    OrderEventEnvelope envelope = buildEnvelope(eventId, "trace-n1", "order-N1", "user-N1");

    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE,
        RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, envelope);

    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      var logs = dispatchLogRepository.findByEventId(eventId);
      assertThat(logs).hasSize(1);
      // Phase 27: status có thể là SENT (SMTP cấu hình) hoặc SKIPPED (graceful degradation — MAIL-01)
      assertThat(logs.get(0).status()).isIn("SENT", "SKIPPED");
      assertThat(logs.get(0).channel()).isEqualTo("email");
      assertThat(logs.get(0).recipientUserId()).isEqualTo("user-N1");
      // subject là MailTemplate.ORDER_CONFIRMATION.subject() — không chứa orderId nữa
      assertThat(logs.get(0).subject()).isEqualTo("Xác nhận đơn hàng");
      assertThat(processedEventRepository.findById(eventId)).isPresent();
    });
  }

  @Test
  void idempotent_samePayloadTwice_dispatchLogOnce() {
    String eventId = UUID.randomUUID().toString();
    OrderEventEnvelope envelope = buildEnvelope(eventId, "trace-n2", "order-N2", "user-N2");

    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE,
        RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, envelope);
    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE,
        RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, envelope);

    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      var logs = dispatchLogRepository.findByEventId(eventId);
      // CHỈ 1 entry dù publish 2 lần — D-06 idempotency
      assertThat(logs).hasSize(1);
    });
  }

  @Test
  void topologyDeclared_queuesAndExchangesExist() {
    // Smoke: queue notification.order-events + DLX/DLQ declared khi context start.
    // Verify gián tiếp qua publish + consume — nếu queue/binding không declare, message
    // không tới consumer → assertion timeout.
    String eventId = UUID.randomUUID().toString();
    OrderEventEnvelope envelope = buildEnvelope(eventId, "trace-n3", "order-N3", "user-N3");

    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE,
        RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, envelope);

    Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
        dispatchLogRepository.findByEventId(eventId).size() == 1);
  }
}
