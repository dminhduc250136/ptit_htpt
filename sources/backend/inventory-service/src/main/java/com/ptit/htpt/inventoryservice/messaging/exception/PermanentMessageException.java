package com.ptit.htpt.inventoryservice.messaging.exception;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * D-08: lỗi vĩnh viễn (payload sai format, business invariant vi phạm, productId không tồn tại).
 * Extend {@link AmqpRejectAndDontRequeueException} để Spring AMQP retry interceptor SKIP retry
 * và route message thẳng vào DLQ (inventory.order-events → order.dlx → order-events.dlq).
 *
 * <p>Pitfall 4 (RESEARCH §lines 488-492): nếu chỉ throw RuntimeException, retry chạy 3 lần vô ích
 * trước khi DLQ — kéo dài MTTR. Extend AmqpRejectAndDontRequeueException đảm bảo 0 retry.
 */
public class PermanentMessageException extends AmqpRejectAndDontRequeueException {
  public PermanentMessageException(String message) {
    super(message);
  }

  public PermanentMessageException(String message, Throwable cause) {
    super(message, cause);
  }
}
