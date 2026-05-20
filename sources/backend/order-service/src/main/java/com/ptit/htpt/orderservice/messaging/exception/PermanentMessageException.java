package com.ptit.htpt.orderservice.messaging.exception;

/**
 * D-08: lỗi vĩnh viễn (payload sai format, business invariant vi phạm).
 * Consumer throw exception này → reject ngay vào DLQ (order-events.dlq), KHÔNG retry.
 * Wave 2 consumer wrap thành AmqpRejectAndDontRequeueException để Spring skip retry interceptor.
 */
public class PermanentMessageException extends RuntimeException {
  public PermanentMessageException(String message) {
    super(message);
  }

  public PermanentMessageException(String message, Throwable cause) {
    super(message, cause);
  }
}
