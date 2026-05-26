package com.ptit.htpt.paymentservice.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JSON envelope cho payment events (PaymentSucceeded / PaymentFailed).
 * Pattern copy từ OrderEventEnvelope (Phase 23).
 *
 * Format: { eventId, eventType, occurredAt, traceId, payload: { orderId, paymentSessionId, ... } }
 * Consumer (order-service) deserialize qua cùng Jackson2JsonMessageConverter.
 *
 * eventType ∈ {"PaymentSucceeded", "PaymentFailed"}.
 */
public record PaymentEventEnvelope(
    String eventId,
    String eventType,
    String occurredAt,
    String traceId,
    PaymentPayload payload
) {
  public static PaymentEventEnvelope of(String eventType, String traceId, PaymentPayload payload) {
    return new PaymentEventEnvelope(
        UUID.randomUUID().toString(),
        eventType,
        Instant.now().toString(),
        traceId,
        payload
    );
  }

  /**
   * Payload của payment event — orderId là field bắt buộc, dùng bởi order-service consumer.
   * paymentTransactionNo: mã giao dịch của cổng thanh toán (chung cho mọi gateway).
   */
  public record PaymentPayload(
      String orderId,
      String paymentSessionId,
      String paymentTransactionNo,
      BigDecimal amount,
      String currency
  ) {}
}
