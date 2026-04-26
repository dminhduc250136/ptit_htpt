package com.ptit.htpt.productservice.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Wire format cho controller response. KHÔNG có field {@code deleted} để tránh
 * leak qua Jackson (Pitfall 3 — Entity↔DTO boundary explicit).
 */
public record ProductDto(
    String id,
    String name,
    String slug,
    String categoryId,
    BigDecimal price,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
