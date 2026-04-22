package com.ptit.htpt.paymentservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSessionEntity(
    String id,
    String orderId,
    String provider,
    BigDecimal amount,
    String status,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static PaymentSessionEntity create(String orderId, String provider, BigDecimal amount, String status) {
    Instant now = Instant.now();
    return new PaymentSessionEntity(UUID.randomUUID().toString(), orderId, provider, amount, status, false, now, now);
  }

  public PaymentSessionEntity update(String orderId, String provider, BigDecimal amount, String status) {
    return new PaymentSessionEntity(id, orderId, provider, amount, status, deleted, createdAt, Instant.now());
  }

  public PaymentSessionEntity setStatus(String status) {
    return new PaymentSessionEntity(id, orderId, provider, amount, status, deleted, createdAt, Instant.now());
  }

  public PaymentSessionEntity softDelete() {
    return new PaymentSessionEntity(id, orderId, provider, amount, status, true, createdAt, Instant.now());
  }
}
