package com.ptit.htpt.productservice.domain;

import java.time.Instant;

/** Wire format cho CategoryEntity. KHÔNG có field {@code deleted}. */
public record CategoryDto(
    String id,
    String name,
    String slug,
    Instant createdAt,
    Instant updatedAt
) {}
