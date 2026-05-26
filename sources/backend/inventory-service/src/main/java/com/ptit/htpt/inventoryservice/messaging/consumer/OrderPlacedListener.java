package com.ptit.htpt.inventoryservice.messaging.consumer;

import com.ptit.htpt.inventoryservice.messaging.event.OrderEventEnvelope;
import com.ptit.htpt.inventoryservice.messaging.exception.PermanentMessageException;
import com.ptit.htpt.inventoryservice.messaging.exception.TransientMessageException;
import com.ptit.htpt.inventoryservice.messaging.tracing.TraceIdConsumerInterceptor;
import com.ptit.htpt.inventoryservice.repository.ProcessedEventRepository;
import com.ptit.htpt.inventoryservice.service.InventoryCrudService;
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
 * D-10: Consume OrderPlaced từ queue inventory.order-events.
 * Idempotent qua processed_events table (D-06).
 * PermanentException → DLQ ngay (D-08 + Pitfall 4: AmqpRejectAndDontRequeueException, 0 retry).
 * TransientException → Spring retry interceptor (D-07: 3 lần exp backoff 1s→2s→4s).
 *
 * <p>Pitfall 8 (RESEARCH §511-514): INSERT processed_events ĐẦU TIÊN trong cùng transaction —
 * nếu business logic fail thì rollback cả INSERT, message retry hoặc DLQ; nếu duplicate eventId
 * thì insertIfAbsent return false → skip business logic + ACK.
 */
@Component
public class OrderPlacedListener {
  private static final Logger log = LoggerFactory.getLogger(OrderPlacedListener.class);
  private static final String QUEUE = "inventory.order-events";
  private static final String EVENT_TYPE = "OrderPlaced";

  private final ProcessedEventRepository processedEventRepository;
  private final InventoryCrudService inventoryCrudService;

  public OrderPlacedListener(ProcessedEventRepository processedEventRepository,
                             InventoryCrudService inventoryCrudService) {
    this.processedEventRepository = processedEventRepository;
    this.inventoryCrudService = inventoryCrudService;
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
      boolean inserted = processedEventRepository.insertIfAbsent(eventId, EVENT_TYPE);
      if (!inserted) {
        log.info("[MQ-CONSUME] queue={} eventId={} status=skipped-duplicate", QUEUE, eventId);
        return;
      }

      // Business logic (D-10): trừ kho + ledger per-item.
      inventoryCrudService.decrementForOrder(
          eventId, envelope.payload().orderId(), envelope.payload().items());

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
