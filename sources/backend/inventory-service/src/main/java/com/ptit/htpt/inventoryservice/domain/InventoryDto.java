package com.ptit.htpt.inventoryservice.domain;

import java.time.Instant;

/**
 * Wire format cho inventory items — controller response.
 * Inventory không có field {@code deleted} (Phase 5 scope-cut), nên DTO mirror nguyên Entity shape.
 */
public record InventoryDto(
    String id,
    String productId,
    int quantity,
    int reserved,
    Instant createdAt,
    Instant updatedAt
) {}
