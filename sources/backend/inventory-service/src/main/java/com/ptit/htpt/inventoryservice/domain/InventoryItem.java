package com.ptit.htpt.inventoryservice.domain;

import java.time.Instant;
import java.util.UUID;

public record InventoryItem(
    String id,
    String sku,
    String name,
    int quantity,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static InventoryItem create(String sku, String name, int quantity) {
    Instant now = Instant.now();
    return new InventoryItem(UUID.randomUUID().toString(), sku, name, quantity, false, now, now);
  }

  public InventoryItem update(String sku, String name, int quantity) {
    return new InventoryItem(id, sku, name, quantity, deleted, createdAt, Instant.now());
  }

  public InventoryItem adjustQuantity(int quantity) {
    return new InventoryItem(id, sku, name, quantity, deleted, createdAt, Instant.now());
  }

  public InventoryItem softDelete() {
    return new InventoryItem(id, sku, name, quantity, true, createdAt, Instant.now());
  }
}
