package com.ptit.htpt.notificationservice.messaging.consumer;

import com.ptit.htpt.notificationservice.messaging.event.OrderEventEnvelope;
import com.ptit.htpt.notificationservice.messaging.exception.PermanentMessageException;
import com.ptit.htpt.notificationservice.messaging.exception.TransientMessageException;
import com.ptit.htpt.notificationservice.messaging.tracing.TraceIdConsumerInterceptor;
import com.ptit.htpt.notificationservice.repository.ProcessedEventRepository;
import com.ptit.htpt.notificationservice.service.NotificationDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-14: Consume OrderPlaced từ queue notification.order-events.
 * Render template "order-confirmation" + insert dispatch_log status=SENT, channel=email.
 * KHÔNG gửi SMTP thật.
 *
 * <p>Idempotent qua processed_events table (D-06).
 * PermanentException → DLQ ngay (D-08 + Pitfall 4: AmqpRejectAndDontRequeueException, 0 retry).
 * TransientException → Spring retry interceptor (D-07: 3 lần exp backoff 1s→2s→4s).
 *
 * <p>Pitfall 8 (RESEARCH §511-514): INSERT processed_events ĐẦU TIÊN trong cùng transaction —
 * nếu render/persist fail thì rollback cả INSERT, message retry hoặc DLQ; nếu duplicate eventId
 * thì insertIfAbsent return false → skip business logic + ACK.
 *
 * <p>Pattern mirror với inventory-service OrderPlacedListener (Plan 23-04) — chỉ khác:
 *  - Queue name: notification.order-events (literal — annotation cần compile-time constant)
 *  - Service call: notificationDispatchService.recordOrderConfirmation(eventId, payload)
 *  - KHÔNG re-declare RabbitMQConfig (đã có Plan 23-03).
 */
@Component
public class OrderPlacedNotifyListener {
  private static final Logger log = LoggerFactory.getLogger(OrderPlacedNotifyListener.class);
  private static final String QUEUE = "notification.order-events";

  private final ProcessedEventRepository processedEventRepository;
  private final NotificationDispatchService notificationDispatchService;

  public OrderPlacedNotifyListener(ProcessedEventRepository processedEventRepository,
                                    NotificationDispatchService notificationDispatchService) {
    this.processedEventRepository = processedEventRepository;
    this.notificationDispatchService = notificationDispatchService;
  }

  @RabbitListener(queues = QUEUE)
  @Transactional
  public void onOrderPlaced(@Payload OrderEventEnvelope envelope,
                             @Header(name = "X-Trace-Id", required = false) String traceIdHeader) {
    TraceIdConsumerInterceptor.enter(traceIdHeader);
    String eventId = envelope.eventId();
    try {
      log.info("[MQ-CONSUME] queue={} eventId={} status=received", QUEUE, eventId);

      // D-06 + Pitfall 8: INSERT processed_events ĐẦU TIÊN trong cùng transaction.
      // Dùng envelope.eventType() thay vì hằng EVENT_TYPE để phân biệt OrderPlaced vs OrderStatusChanged.
      boolean inserted = processedEventRepository.insertIfAbsent(eventId, envelope.eventType());
      if (!inserted) {
        log.info("[MQ-CONSUME] queue={} eventId={} status=skipped-duplicate", QUEUE, eventId);
        return;
      }

      // Business logic (D-14/Phase 27): branch theo eventType, gọi đúng method service.
      switch (envelope.eventType()) {
        case "OrderPlaced" ->
            notificationDispatchService.sendOrderConfirmation(eventId, envelope.orderPlacedPayload());
        case "OrderStatusChanged" -> {
          // Chỉ shipped/delivered/cancelled gửi email — bỏ qua pending/confirmed.
          // Order-service publish status UPPERCASE (vd "SHIPPED") nên lowercase trước khi match.
          String status = envelope.orderStatusChangedPayload().newStatus();
          String norm = status == null ? "" : status.toLowerCase(java.util.Locale.ROOT);
          com.ptit.htpt.notificationservice.service.email.MailTemplate template = switch (norm) {
            case "shipped"   -> com.ptit.htpt.notificationservice.service.email.MailTemplate.ORDER_SHIPPED;
            case "delivered" -> com.ptit.htpt.notificationservice.service.email.MailTemplate.ORDER_DELIVERED;
            case "cancelled" -> com.ptit.htpt.notificationservice.service.email.MailTemplate.ORDER_CANCELLED;
            default          -> null;
          };
          if (template != null) {
            notificationDispatchService.sendOrderStatusChanged(
                eventId, envelope.orderStatusChangedPayload(), template);
          }
        }
        default -> throw new PermanentMessageException("Unknown order event: " + envelope.eventType());
      }

      log.info("[MQ-CONSUME] queue={} eventId={} status=done", QUEUE, eventId);
    } catch (PermanentMessageException e) {
      // PermanentMessageException đã extend AmqpRejectAndDontRequeueException → Spring skip retry.
      log.error("[MQ-DLQ] eventId={} reason={} payload={}", eventId, e.getMessage(), envelope);
      throw e;
    } catch (DataAccessResourceFailureException | TransientDataAccessException e) {
      log.warn("[MQ-RETRY] eventId={} error={}", eventId, e.getMessage());
      throw new TransientMessageException("DB transient on consume eventId=" + eventId, e);
    } catch (RuntimeException e) {
      // Mặc định coi như permanent — tránh retry vô tận với lỗi không lường trước.
      log.error("[MQ-DLQ] eventId={} reason=unexpected error={} payload={}",
          eventId, e.getMessage(), envelope);
      throw new AmqpRejectAndDontRequeueException("Unexpected: " + e.getMessage(), e);
    } finally {
      TraceIdConsumerInterceptor.exit();
    }
  }
}
