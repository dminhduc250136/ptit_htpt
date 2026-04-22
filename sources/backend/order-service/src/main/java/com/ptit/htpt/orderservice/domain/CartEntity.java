package com.ptit.htpt.orderservice.domain;

import java.time.Instant;
import java.util.UUID;

public record CartEntity(
    String id,
    String userId,
    String productId,
    int quantity,
    String status,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static CartEntity create(String userId, String productId, int quantity, String status) {
    Instant now = Instant.now();
    return new CartEntity(UUID.randomUUID().toString(), userId, productId, quantity, status, false, now, now);
  }

  public CartEntity update(String userId, String productId, int quantity, String status) {
    return new CartEntity(id, userId, productId, quantity, status, deleted, createdAt, Instant.now());
  }

  public CartEntity softDelete() {
    return new CartEntity(id, userId, productId, quantity, status, true, createdAt, Instant.now());
  }
}
