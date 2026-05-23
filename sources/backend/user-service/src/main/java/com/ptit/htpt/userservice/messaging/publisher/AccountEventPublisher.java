package com.ptit.htpt.userservice.messaging.publisher;

import com.ptit.htpt.userservice.messaging.config.UserRabbitMQConfig;
import com.ptit.htpt.userservice.messaging.event.UserEventEnvelope;
import com.ptit.htpt.userservice.messaging.tracing.TraceIdMessagePostProcessor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Phase 27 / Plan 27-02 (MAIL-02): Publish user account events SAU khi DB transaction commit.
 *
 * afterCommit pattern (D-03): đảm bảo event KHÔNG phát nếu DB rollback.
 * Pitfall 1 (RESEARCH §469-474): capture MDC traceId NGAY tại caller thread — KHÔNG trong callback.
 *
 * Publisher Confirms timeout 5s (D-04): nếu broker không ack → log error.
 * KHÔNG rollback logic — event fail → log; Wave 3 notification-service sẽ consume.
 *
 * Log contract (D-17):
 *   [MQ-PUB]         success
 *   [MQ-PUB-NACK]    broker nack
 *   [MQ-PUB-TIMEOUT] confirm timeout 5s
 *   [MQ-PUB-INTERRUPT] thread interrupted
 *   [MQ-PUB-ERR]     exception khi convertAndSend
 *
 * KHÔNG Lombok — explicit constructor injection (1 dep).
 */
@Component
public class AccountEventPublisher {
  private static final Logger log = LoggerFactory.getLogger(AccountEventPublisher.class);
  private static final long CONFIRM_TIMEOUT_MS = 5000L;

  private final RabbitTemplate rabbitTemplate;

  public AccountEventPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  /**
   * Publish event UserRegistered SAU khi DB transaction commit.
   * Gọi từ AuthService.register() sau khi userRepo.save().
   *
   * @param payload UserPayload đã build từ saved UserEntity
   */
  public void publishUserRegistered(UserEventEnvelope.UserPayload payload) {
    // Pitfall 1: capture MDC traceId NGAY (afterCommit callback có thể chạy sau MDC cleanup)
    String traceId = MDC.get("traceId");
    final String safeTraceId = traceId != null ? traceId : "no-trace";
    final UserEventEnvelope envelope = UserEventEnvelope.createUserRegistered(safeTraceId, payload);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          doPublish(envelope, UserRabbitMQConfig.ROUTING_KEY_REGISTERED, safeTraceId);
        }
      });
    } else {
      // Fallback: caller không trong transaction (vd: test unit) → publish ngay
      doPublish(envelope, UserRabbitMQConfig.ROUTING_KEY_REGISTERED, safeTraceId);
    }
  }

  /**
   * Publish event PasswordResetRequested SAU khi DB transaction commit.
   * Gọi từ service xử lý forgot-password sau khi tạo token.
   *
   * @param payload UserPayload đã build (actionUrl chứa reset token)
   */
  public void publishPasswordReset(UserEventEnvelope.UserPayload payload) {
    // Pitfall 1: capture MDC traceId NGAY (afterCommit callback có thể chạy sau MDC cleanup)
    String traceId = MDC.get("traceId");
    final String safeTraceId = traceId != null ? traceId : "no-trace";
    final UserEventEnvelope envelope = UserEventEnvelope.createPasswordReset(safeTraceId, payload);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          doPublish(envelope, UserRabbitMQConfig.ROUTING_KEY_PASSWORD_RESET, safeTraceId);
        }
      });
    } else {
      // Fallback: caller không trong transaction (vd: test unit) → publish ngay
      doPublish(envelope, UserRabbitMQConfig.ROUTING_KEY_PASSWORD_RESET, safeTraceId);
    }
  }

  /**
   * Thực thi publish lên RabbitMQ với Publisher Confirms (D-04).
   *
   * @param envelope   envelope đã build
   * @param routingKey routing key (vd "user.registered", "user.password-reset")
   * @param traceId    đã capture tại caller thread (Pitfall 1)
   */
  private void doPublish(UserEventEnvelope envelope, String routingKey, String traceId) {
    String eventId = envelope.eventId();
    CorrelationData correlation = new CorrelationData(eventId);
    TraceIdMessagePostProcessor traceProcessor = new TraceIdMessagePostProcessor(traceId);

    try {
      rabbitTemplate.convertAndSend(
          UserRabbitMQConfig.USER_EXCHANGE,
          routingKey,
          envelope,
          msg -> {
            msg.getMessageProperties().setMessageId(eventId);
            return traceProcessor.postProcessMessage(msg);
          },
          correlation
      );

      // Publisher Confirms — block tối đa CONFIRM_TIMEOUT_MS (D-04)
      CorrelationData.Confirm confirm = correlation.getFuture()
          .get(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      if (confirm == null || !confirm.isAck()) {
        String reason = confirm == null ? "null" : confirm.getReason();
        log.error("[MQ-PUB-NACK] eventId={} traceId={} reason={}", eventId, traceId, reason);
      } else {
        log.info("[MQ-PUB] event={} routingKey={} eventId={} traceId={}",
            envelope.eventType(), routingKey, eventId, traceId);
      }
    } catch (TimeoutException e) {
      log.error("[MQ-PUB-TIMEOUT] eventId={} traceId={}", eventId, traceId);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("[MQ-PUB-INTERRUPT] eventId={} traceId={}", eventId, traceId);
    } catch (Exception e) {
      log.error("[MQ-PUB-ERR] eventId={} traceId={} err={}", eventId, traceId, e.getMessage(), e);
    }
  }
}
