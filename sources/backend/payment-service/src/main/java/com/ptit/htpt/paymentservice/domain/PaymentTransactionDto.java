package com.ptit.htpt.paymentservice.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Wire format — KHÔNG có field deleted để tránh leak qua Jackson. */
public record PaymentTransactionDto(
    String id,
    String sessionId,
    String reference,
    String message,
    BigDecimal amount,
    String method,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
