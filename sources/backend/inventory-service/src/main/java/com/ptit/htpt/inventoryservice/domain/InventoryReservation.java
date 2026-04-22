package com.ptit.htpt.inventoryservice.domain;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservation(
    String id,
    String itemId,
    String orderId,
    int quantity,
    String status,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static InventoryReservation create(String itemId, String orderId, int quantity, String status) {
    Instant now = Instant.now();
    return new InventoryReservation(UUID.randomUUID().toString(), itemId, orderId, quantity, status, false, now, now);
  }

  public InventoryReservation update(String itemId, String orderId, int quantity, String status) {
    return new InventoryReservation(id, itemId, orderId, quantity, status, deleted, createdAt, Instant.now());
  }

  public InventoryReservation softDelete() {
    return new InventoryReservation(id, itemId, orderId, quantity, status, true, createdAt, Instant.now());
  }
}
