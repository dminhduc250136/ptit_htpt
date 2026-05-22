package com.ptit.htpt.paymentservice.messaging.publisher;

import com.ptit.htpt.paymentservice.messaging.config.RabbitMQConfig;
import com.ptit.htpt.paymentservice.messaging.event.PaymentEventEnvelope;
import com.ptit.htpt.paymentservice.messaging.tracing.TraceIdMessagePostProcessor;
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
 * Publish PaymentSucceeded / PaymentFailed event SAU khi DB transaction commit.
 * Pattern copy từ OrderEventPublisher (Phase 23).
 *
 * Publisher Confirms timeout 5s: nếu broker không ack → log error.
 * KHÔNG rollback transaction khi publish fail — DB đã saved, nhất quán user-facing.
 *
 * Trace propagation: X-Trace-Id header set qua TraceIdMessagePostProcessor.
 * MDC capture NGAY tại caller thread (Pitfall 1 — afterCommit có thể chạy sau MDC cleanup).
 *
 * Log contract:
 *   [MQ-PUB]         success
 *   [MQ-PUB-NACK]    broker nack
 *   [MQ-PUB-TIMEOUT] confirm timeout 5s
 *   [MQ-PUB-ERR]     exception khi convertAndSend
 */
@Component
public class PaymentEventPublisher {
  private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
  private static final long CONFIRM_TIMEOUT_MS = 5000L;

  private final RabbitTemplate paymentRabbitTemplate;

  public PaymentEventPublisher(RabbitTemplate paymentRabbitTemplate) {
    this.paymentRabbitTemplate = paymentRabbitTemplate;
  }

  /**
   * Defer publish đến afterCommit callback của transaction hiện tại.
   *
   * @param eventType  "PaymentSucceeded" | "PaymentFailed"
   * @param payload    payment payload
   */
  public void publishPaymentEvent(String eventType, PaymentEventEnvelope.PaymentPayload payload) {
    // Pitfall 1: capture MDC traceId NGAY (afterCommit callback có thể chạy sau MDC cleanup)
    String traceId = MDC.get("traceId");
    final String safeTraceId = traceId != null ? traceId : "no-trace";
    final PaymentEventEnvelope envelope = PaymentEventEnvelope.of(eventType, safeTraceId, payload);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          doPublish(envelope, safeTraceId, eventType);
        }
      });
    } else {
      // Fallback: caller không trong transaction (vd: test unit) → publish ngay
      doPublish(envelope, safeTraceId, eventType);
    }
  }

  private void doPublish(PaymentEventEnvelope envelope, String traceId, String eventType) {
    String eventId = envelope.eventId();
    String routingKey = "PaymentSucceeded".equals(eventType)
        ? RabbitMQConfig.ROUTING_KEY_SUCCEEDED
        : RabbitMQConfig.ROUTING_KEY_FAILED;

    CorrelationData correlation = new CorrelationData(eventId);
    TraceIdMessagePostProcessor traceProcessor = new TraceIdMessagePostProcessor(traceId);

    try {
      paymentRabbitTemplate.convertAndSend(
          RabbitMQConfig.EXCHANGE,
          routingKey,
          envelope,
          msg -> {
            msg.getMessageProperties().setMessageId(eventId);
            return traceProcessor.postProcessMessage(msg);
          },
          correlation
      );

      // Publisher Confirms — block tối đa CONFIRM_TIMEOUT_MS
      CorrelationData.Confirm confirm = correlation.getFuture()
          .get(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      if (confirm == null || !confirm.isAck()) {
        String reason = confirm == null ? "null" : confirm.getReason();
        log.error("[MQ-PUB-NACK] eventId={} traceId={} reason={}", eventId, traceId, reason);
      } else {
        log.info("[MQ-PUB] event={} routingKey={} eventId={} traceId={}",
            eventType, routingKey, eventId, traceId);
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
