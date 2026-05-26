package com.ptit.htpt.orderservice.messaging.consumer;

import com.ptit.htpt.orderservice.domain.OrderEntity;
import com.ptit.htpt.orderservice.messaging.event.PaymentEventEnvelope;
import com.ptit.htpt.orderservice.messaging.exception.PermanentMessageException;
import com.ptit.htpt.orderservice.messaging.exception.TransientMessageException;
import com.ptit.htpt.orderservice.messaging.tracing.TraceIdConsumerInterceptor;
import com.ptit.htpt.orderservice.messaging.publisher.OrderEventPublisher;
import com.ptit.htpt.orderservice.repository.OrderRepository;
import com.ptit.htpt.orderservice.repository.ProcessedEventRepository;
import com.ptit.htpt.orderservice.service.OrderCrudService;
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
 * Phase 26 / Plan 26-03 (D-09, T-26-09): Consume PaymentSucceeded / PaymentFailed từ exchange
 * {@code payment.events}, queue {@code order.payment-events}.
 *
 * <p>Phase 26.1 (D-07): setter rename setVnpTransactionNo → setPaymentTransactionNo;
 * payload getter rename vnpTransactionNo() → paymentTransactionNo() (envelope shape sync Plan 01).
 *
 * <p>Behavior:
 *   - PaymentSucceeded → set order.paymentStatus=PAID + set paymentTransactionNo + publish OrderPlaced (D-09)
 *   - PaymentFailed    → set order.paymentStatus=FAILED, KHÔNG publish OrderPlaced
 *
 * <p>Idempotency (T-26-09 Pitfall 8): insertIfAbsent processed_events ĐẦU TIÊN trong cùng
 * @Transactional. Duplicate eventId → log skipped-duplicate + ACK (không double-update).
 *
 * <p>Error classification (T-26-12) — copy pattern OrderPlacedListener (inventory):
 *   PermanentMessageException → AmqpRejectAndDontRequeueException (DLQ ngay)
 *   TransientDataAccessException → TransientMessageException (Spring retry 3× exp backoff)
 *   RuntimeException → AmqpRejectAndDontRequeueException (unknown = permanent, tránh retry vô tận)
 */
@Component
public class PaymentEventListener {

  private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);
  private static final String QUEUE = "order.payment-events";

  private final ProcessedEventRepository processedEventRepository;
  private final OrderRepository orderRepository;
  private final OrderCrudService orderCrudService;

  public PaymentEventListener(ProcessedEventRepository processedEventRepository,
                               OrderRepository orderRepository,
                               OrderCrudService orderCrudService) {
    this.processedEventRepository = processedEventRepository;
    this.orderRepository = orderRepository;
    this.orderCrudService = orderCrudService;
  }

  @RabbitListener(queues = QUEUE)
  @Transactional
  public void onPaymentEvent(@Payload PaymentEventEnvelope envelope,
                              @Header(name = "X-Trace-Id", required = false) String traceIdHeader) {
    TraceIdConsumerInterceptor.enter(traceIdHeader);
    String eventId = envelope.eventId();
    try {
      log.info("[MQ-CONSUME] queue={} eventId={} eventType={} status=received",
          QUEUE, eventId, envelope.eventType());

      // T-26-09 + Pitfall 8: INSERT processed_events ĐẦU TIÊN trong cùng transaction.
      // Nếu duplicate eventId → insertIfAbsent trả false → skip business logic + ACK.
      boolean inserted = processedEventRepository.insertIfAbsent(eventId, envelope.eventType());
      if (!inserted) {
        log.info("[MQ-CONSUME] queue={} eventId={} status=skipped-duplicate", QUEUE, eventId);
        return;
      }

      // Business logic
      PaymentEventEnvelope.PaymentPayload payload = envelope.payload();
      String orderId = payload.orderId();
      OrderEntity order = orderRepository.findByIdWithItems(orderId)
          .orElseThrow(() -> new PermanentMessageException(
              "Order not found: orderId=" + orderId + " eventId=" + eventId));

      if ("PaymentSucceeded".equals(envelope.eventType())) {
        // D-09: IPN xác nhận PAID → trừ kho (publish OrderPlaced) lúc này
        // Phase 26.1 (D-07): setter + payload getter rename để khớp envelope shape Plan 01
        order.setPaymentStatus("PAID");
        order.setPaymentTransactionNo(payload.paymentTransactionNo());
        orderRepository.save(order);
        // Helper DRY từ OrderCrudService — build payload + publish afterCommit (D-09)
        orderCrudService.publishOrderPlacedForOrder(order);
        log.info("[MQ-CONSUME] queue={} eventId={} orderId={} status=done action=PAID+OrderPlaced",
            QUEUE, eventId, orderId);

      } else if ("PaymentFailed".equals(envelope.eventType())) {
        // D-09: thanh toán thất bại → FAILED, KHÔNG publish OrderPlaced
        order.setPaymentStatus("FAILED");
        orderRepository.save(order);
        log.info("[MQ-CONSUME] queue={} eventId={} orderId={} status=done action=FAILED",
            QUEUE, eventId, orderId);

      } else {
        // Unknown eventType — coi là permanent (tránh retry vô tận)
        throw new PermanentMessageException(
            "Unknown eventType=" + envelope.eventType() + " eventId=" + eventId);
      }

    } catch (PermanentMessageException e) {
      // PermanentMessageException → DLQ ngay (order-service exception extends RuntimeException,
      // cần wrap thành AmqpRejectAndDontRequeueException để Spring skip retry)
      log.error("[MQ-DLQ] eventId={} reason={} payload={}", eventId, e.getMessage(), envelope);
      throw new AmqpRejectAndDontRequeueException("Permanent: " + e.getMessage(), e);
    } catch (DataAccessResourceFailureException | TransientDataAccessException e) {
      log.warn("[MQ-RETRY] eventId={} error={}", eventId, e.getMessage());
      throw new TransientMessageException("DB transient on consume eventId=" + eventId, e);
    } catch (AmqpRejectAndDontRequeueException e) {
      throw e; // đã wrap ở trên, re-throw thẳng
    } catch (RuntimeException e) {
      // Fallback: unknown error coi permanent — tránh retry vô tận (T-26-12)
      log.error("[MQ-DLQ] eventId={} reason=unexpected error={} payload={}",
          eventId, e.getMessage(), envelope);
      throw new AmqpRejectAndDontRequeueException("Unexpected: " + e.getMessage(), e);
    } finally {
      TraceIdConsumerInterceptor.exit();
    }
  }
}
