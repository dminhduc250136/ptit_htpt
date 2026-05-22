package com.ptit.htpt.orderservice.messaging.publisher;

import com.ptit.htpt.orderservice.messaging.config.RabbitMQConfig;
import com.ptit.htpt.orderservice.messaging.event.OrderEventEnvelope;
import com.ptit.htpt.orderservice.messaging.tracing.TraceIdMessagePostProcessor;
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
 * Publish OrderPlaced event SAU khi DB transaction commit (D-03).
 *
 * Publisher Confirms timeout 5s (D-04): nếu broker không ack trong 5s → log error + alert.
 * KHÔNG rollback order khi publish fail — order đã saved trong DB, nhất quán user-facing
 * quan trọng hơn (D-05). Admin replay defer sang phase sau.
 *
 * Trace propagation (D-16): X-Trace-Id header set qua TraceIdMessagePostProcessor.
 * Pitfall 1 (RESEARCH §469-474): MDC.get("traceId") trong afterCommit callback có thể
 * trả null vì callback chạy sau khi servlet filter cleanup MDC. Vì vậy capture traceId
 * NGAY tại caller thread (đầu method publishOrderPlaced), pass qua closure.
 *
 * Log contract (D-17):
 *   [MQ-PUB]         success
 *   [MQ-PUB-NACK]    broker nack
 *   [MQ-PUB-TIMEOUT] confirm timeout 5s
 *   [MQ-PUB-ERR]     exception khi convertAndSend
 */
@Component
public class OrderEventPublisher {
  private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
  private static final long CONFIRM_TIMEOUT_MS = 5000L;

  private final RabbitTemplate rabbitTemplate;

  public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  /**
   * Defer publish đến afterCommit callback của transaction hiện tại.
   * Gọi từ OrderCrudService.createOrderFromCommand sau khi orderRepository.save().
   *
   * @param payload OrderPlacedPayload đã build từ saved OrderEntity
   */
  public void publishOrderPlaced(OrderEventEnvelope.OrderPlacedPayload payload) {
    // Pitfall 1: capture MDC traceId NGAY (afterCommit callback có thể chạy sau MDC cleanup)
    String traceId = MDC.get("traceId");
    final String safeTraceId = traceId != null ? traceId : "no-trace";
    final OrderEventEnvelope envelope = OrderEventEnvelope.createOrderPlaced(safeTraceId, payload);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          doPublish(envelope, RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, safeTraceId);
        }
      });
    } else {
      // Fallback: caller không trong transaction (vd: test unit) → publish ngay
      doPublish(envelope, RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, safeTraceId);
    }
  }

  /**
   * Publish event OrderStatusChanged SAU khi DB transaction commit (afterCommit).
   * Gọi từ OrderCrudService.updateOrderState sau khi orderRepository.save().
   *
   * Phase 27 D-12: phát event khi admin đổi trạng thái đơn → notification-service gửi email.
   * routing key "order.status-changed" bind vào queue "notification.order-events" qua "order.#".
   *
   * @param payload OrderStatusChangedPayload đã build từ saved OrderEntity
   */
  public void publishOrderStatusChanged(OrderEventEnvelope.OrderStatusChangedPayload payload) {
    // Pitfall 1: capture MDC traceId NGAY (afterCommit callback có thể chạy sau MDC cleanup)
    String traceId = MDC.get("traceId");
    final String safeTraceId = traceId != null ? traceId : "no-trace";
    final OrderEventEnvelope envelope = OrderEventEnvelope.createOrderStatusChanged(safeTraceId, payload);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          doPublish(envelope, RabbitMQConfig.ROUTING_KEY_ORDER_STATUS_CHANGED, safeTraceId);
        }
      });
    } else {
      // Fallback: caller không trong transaction (vd: test unit) → publish ngay
      doPublish(envelope, RabbitMQConfig.ROUTING_KEY_ORDER_STATUS_CHANGED, safeTraceId);
    }
  }

  /**
   * Thực thi publish lên RabbitMQ với Publisher Confirms (D-04).
   * Refactor Task 2 Phase 27: thêm tham số routingKey để dùng chung cho
   * cả OrderPlaced và OrderStatusChanged.
   *
   * @param envelope  envelope đã build (bao gồm eventId, eventType, payload)
   * @param routingKey routing key gửi lên exchange (vd: "order.placed", "order.status-changed")
   * @param traceId   đã capture tại caller thread (Pitfall 1)
   */
  private void doPublish(OrderEventEnvelope envelope, String routingKey, String traceId) {
    String eventId = envelope.eventId();
    CorrelationData correlation = new CorrelationData(eventId);
    TraceIdMessagePostProcessor traceProcessor = new TraceIdMessagePostProcessor(traceId);

    try {
      rabbitTemplate.convertAndSend(
          RabbitMQConfig.EXCHANGE,
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
