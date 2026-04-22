package com.ptit.htpt.paymentservice.domain;

import java.time.Instant;
import java.util.UUID;

public record PaymentTransactionEntity(
    String id,
    String sessionId,
    String reference,
    String status,
    String message,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static PaymentTransactionEntity create(
      String sessionId,
      String reference,
      String status,
      String message
  ) {
    Instant now = Instant.now();
    return new PaymentTransactionEntity(UUID.randomUUID().toString(), sessionId, reference, status, message, false, now, now);
  }

  public PaymentTransactionEntity update(String sessionId, String reference, String status, String message) {
    return new PaymentTransactionEntity(id, sessionId, reference, status, message, deleted, createdAt, Instant.now());
  }

  public PaymentTransactionEntity setStatus(String status) {
    return new PaymentTransactionEntity(id, sessionId, reference, status, message, deleted, createdAt, Instant.now());
  }

  public PaymentTransactionEntity softDelete() {
    return new PaymentTransactionEntity(id, sessionId, reference, status, message, true, createdAt, Instant.now());
  }
}
