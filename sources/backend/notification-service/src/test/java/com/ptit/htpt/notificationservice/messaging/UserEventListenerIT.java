package com.ptit.htpt.notificationservice.messaging;

import com.ptit.htpt.notificationservice.messaging.config.RabbitMQConfig;
import com.ptit.htpt.notificationservice.messaging.event.UserEventEnvelope;
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
 * Phase 27 / Plan 27-05 — IT cho UserEventListener.
 *
 * <p>4 scenario theo plan:
 * 1. UserRegistered → dispatch_log ACCOUNT_VERIFICATION ghi nhận.
 * 2. PasswordResetRequested → dispatch_log PASSWORD_RESET ghi nhận.
 * 3. Idempotent: cùng eventId 2 lần → chỉ 1 dispatch_log.
 * 4. Topology smoke: queue notification.user-events tồn tại và consumer nhận message.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class UserEventListenerIT {

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

  private UserEventEnvelope buildEnvelope(String eventId, String eventType, String traceId) {
    UserEventEnvelope.UserPayload payload = new UserEventEnvelope.UserPayload(
        "user-u1", "test@example.com", "Test User",
        "http://localhost:3000/verify-email?token=abc123"
    );
    return new UserEventEnvelope(eventId, eventType, Instant.now().toString(), traceId, payload);
  }

  /**
   * Test 4 theo plan: UserRegistered → dispatch_log subject ACCOUNT_VERIFICATION.
   */
  @Test
  void userRegistered_consumeEvent_dispatchLogCreated() {
    String eventId = UUID.randomUUID().toString();
    UserEventEnvelope envelope = buildEnvelope(eventId, "UserRegistered", "trace-u1");

    rabbitTemplate.convertAndSend(
        RabbitMQConfig.USER_EXCHANGE, "user.registered", envelope);

    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      var logs = dispatchLogRepository.findByEventId(eventId);
      assertThat(logs).hasSize(1);
      assertThat(logs.get(0).status()).isIn("SENT", "SKIPPED");
      assertThat(logs.get(0).channel()).isEqualTo("email");
      assertThat(logs.get(0).subject()).isEqualTo("Xác minh địa chỉ email của bạn");
      assertThat(processedEventRepository.findById(eventId)).isPresent();
    });
  }

  /**
   * PasswordResetRequested → dispatch_log PASSWORD_RESET ghi nhận.
   */
  @Test
  void passwordResetRequested_consumeEvent_dispatchLogCreated() {
    String eventId = UUID.randomUUID().toString();
    UserEventEnvelope.UserPayload payload = new UserEventEnvelope.UserPayload(
        "user-u2", "reset@example.com", "Reset User",
        "http://localhost:3000/reset-password?token=xyz789"
    );
    UserEventEnvelope envelope = new UserEventEnvelope(
        eventId, "PasswordResetRequested", Instant.now().toString(), "trace-u2", payload);

    rabbitTemplate.convertAndSend(
        RabbitMQConfig.USER_EXCHANGE, "user.password-reset", envelope);

    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      var logs = dispatchLogRepository.findByEventId(eventId);
      assertThat(logs).hasSize(1);
      assertThat(logs.get(0).status()).isIn("SENT", "SKIPPED");
      assertThat(logs.get(0).subject()).isEqualTo("Đặt lại mật khẩu");
    });
  }

  /**
   * Idempotent: cùng eventId xử lý 2 lần → chỉ 1 dispatch_log.
   */
  @Test
  void idempotent_sameEventIdTwice_dispatchLogOnce() {
    String eventId = UUID.randomUUID().toString();
    UserEventEnvelope envelope = buildEnvelope(eventId, "UserRegistered", "trace-u3");

    rabbitTemplate.convertAndSend(RabbitMQConfig.USER_EXCHANGE, "user.registered", envelope);
    rabbitTemplate.convertAndSend(RabbitMQConfig.USER_EXCHANGE, "user.registered", envelope);

    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      var logs = dispatchLogRepository.findByEventId(eventId);
      assertThat(logs).hasSize(1);
    });
  }
}
