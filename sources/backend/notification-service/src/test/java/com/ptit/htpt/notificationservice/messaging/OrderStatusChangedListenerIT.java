package com.ptit.htpt.notificationservice.messaging;

import com.ptit.htpt.notificationservice.messaging.config.RabbitMQConfig;
import com.ptit.htpt.notificationservice.messaging.event.OrderEventEnvelope;
import com.ptit.htpt.notificationservice.repository.DispatchLogRepository;
import com.ptit.htpt.notificationservice.repository.ProcessedEventRepository;
import java.time.Duration;
import java.time.Instant;
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
 * Phase 27 / Plan 27-05 — IT cho OrderStatusChanged branch trong OrderPlacedNotifyListener.
 *
 * <p>3 scenario theo plan:
 * Test 1: OrderStatusChanged newStatus="shipped" → dispatch_log subject ORDER_SHIPPED.
 * Test 2: OrderStatusChanged newStatus="confirmed" → KHÔNG ghi dispatch_log (template null → bỏ qua).
 * Test 3: cùng eventId 2 lần → chỉ 1 dispatch_log (idempotent qua processed_events).
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class OrderStatusChangedListenerIT {

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

  private OrderEventEnvelope buildStatusChangedEnvelope(String eventId, String traceId, String newStatus) {
    return new OrderEventEnvelope(
        eventId, "OrderStatusChanged", Instant.now().toString(), traceId,
        new OrderEventEnvelope.OrderStatusChangedPayload(
            "order-s1", "user-s1", "customer@example.com", newStatus, "Test Customer"
        )
    );
  }

  /**
   * Test 1: OrderStatusChanged newStatus="shipped" → dispatch_log subject ORDER_SHIPPED.
   */
  @Test
  void orderStatusChanged_shipped_dispatchLogCreated() {
    String eventId = UUID.randomUUID().toString();
    OrderEventEnvelope envelope = buildStatusChangedEnvelope(eventId, "trace-s1", "shipped");

    rabbitTemplate.convertAndSend(
        RabbitMQConfig.EXCHANGE, "order.status-changed", envelope);

    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      var logs = dispatchLogRepository.findByEventId(eventId);
      assertThat(logs).hasSize(1);
      assertThat(logs.get(0).status()).isIn("SENT", "SKIPPED");
      assertThat(logs.get(0).channel()).isEqualTo("email");
      // ORDER_SHIPPED subject
      assertThat(logs.get(0).subject()).contains("Đơn hàng");
      assertThat(processedEventRepository.findById(eventId)).isPresent();
    });
  }

  /**
   * Test 2: OrderStatusChanged newStatus="confirmed" → KHÔNG ghi dispatch_log (bỏ qua).
   * processed_events vẫn ghi (idempotency marker) nhưng dispatch_log không có bản ghi mới.
   * Dùng Awaitility để chờ processed_events được ghi (chứng tỏ consumer đã xử lý) rồi assert empty log.
   */
  @Test
  void orderStatusChanged_confirmed_noDispatchLog() {
    String eventId = UUID.randomUUID().toString();
    OrderEventEnvelope envelope = buildStatusChangedEnvelope(eventId, "trace-s2", "confirmed");

    rabbitTemplate.convertAndSend(
        RabbitMQConfig.EXCHANGE, "order.status-changed", envelope);

    // Chờ consumer xử lý (processed_events xuất hiện = consumer đã chạy xong)
    Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
        processedEventRepository.findById(eventId).isPresent());

    // Sau khi consumer xong, dispatch_log phải trống (confirmed → không gửi email)
    var logs = dispatchLogRepository.findByEventId(eventId);
    assertThat(logs).isEmpty();
  }

  /**
   * Test 3: cùng eventId xử lý 2 lần → chỉ 1 dispatch_log (idempotent).
   */
  @Test
  void idempotent_sameEventIdTwice_dispatchLogOnce() {
    String eventId = UUID.randomUUID().toString();
    OrderEventEnvelope envelope = buildStatusChangedEnvelope(eventId, "trace-s3", "delivered");

    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "order.status-changed", envelope);
    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "order.status-changed", envelope);

    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      var logs = dispatchLogRepository.findByEventId(eventId);
      assertThat(logs).hasSize(1);
    });
  }
}
