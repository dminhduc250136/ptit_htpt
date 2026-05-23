package com.ptit.htpt.orderservice.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Phase 26 / Plan 26-03 (Plan 01 contract): wire-format envelope cho PaymentSucceeded / PaymentFailed.
 *
 * <p>Shape IDENTICAL với payment-service PaymentEventEnvelope — Jackson2JsonMessageConverter
 * deserialize qua field name matching (KHÔNG cần shared module).
 *
 * <p>Phase 26.1 / Plan 26.1-02 (D-07, T-26.1-11): field rename vnpTransactionNo →
 * paymentTransactionNo để khớp shape mới của payment-service (commit f9ee819).
 * Jackson deserialize qua field name — shape PHẢI IDENTICAL giữa 2 service.
 *
 * <p>eventType ∈ {"PaymentSucceeded", "PaymentFailed"}
 * exchange "payment.events"; routing key "payment.succeeded" / "payment.failed".
 */
public record PaymentEventEnvelope(
    String eventId,
    String eventType,
    String occurredAt,
    String traceId,
    PaymentPayload payload
) {

  /**
   * Factory method — mirror của payment-service PaymentEventEnvelope.of(...)
   * Dùng cho test fabrication.
   */
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
   * Payload chứa thông tin giao dịch — IDENTICAL shape với payment-service PaymentPayload.
   *
   * <p>orderId — map tới order-service OrderEntity.id.
   * paymentTransactionNo — lưu vào orders.payment_transaction_no khi PAID (Phase 26.1 D-07 rename).
   * Jackson deserialize qua field name matching — shape phải khớp payment-service (commit f9ee819).
   */
  public record PaymentPayload(
      String orderId,
      String paymentSessionId,
      String paymentTransactionNo,
      BigDecimal amount,
      String currency
  ) {}
}
