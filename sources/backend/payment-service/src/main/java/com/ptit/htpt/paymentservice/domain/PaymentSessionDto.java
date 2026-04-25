package com.ptit.htpt.paymentservice.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentSessionDto(
    String id,
    String orderId,
    String provider,
    BigDecimal amount,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
