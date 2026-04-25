package com.ptit.htpt.orderservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderEntity(
    String id,
    String userId,
    BigDecimal totalAmount,
    String status,
    String note,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static OrderEntity create(String userId, BigDecimal totalAmount, String status, String note) {
    Instant now = Instant.now();
    return new OrderEntity(UUID.randomUUID().toString(), userId, totalAmount, status, note, false, now, now);
  }

  public OrderEntity update(String userId, BigDecimal totalAmount, String status, String note) {
    return new OrderEntity(id, userId, totalAmount, status, note, deleted, createdAt, Instant.now());
  }

  public OrderEntity setStatus(String status) {
    return new OrderEntity(id, userId, totalAmount, status, note, deleted, createdAt, Instant.now());
  }

  public OrderEntity softDelete() {
    return new OrderEntity(id, userId, totalAmount, status, note, true, createdAt, Instant.now());
  }
}
