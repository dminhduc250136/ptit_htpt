package com.ptit.htpt.productservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductEntity(
    String id,
    String name,
    String slug,
    String categoryId,
    BigDecimal price,
    String status,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static ProductEntity create(
      String name,
      String slug,
      String categoryId,
      BigDecimal price,
      String status
  ) {
    Instant now = Instant.now();
    return new ProductEntity(UUID.randomUUID().toString(), name, slug, categoryId, price, status, false, now, now);
  }

  public ProductEntity update(String name, String slug, String categoryId, BigDecimal price, String status) {
    return new ProductEntity(id, name, slug, categoryId, price, status, deleted, createdAt, Instant.now());
  }

  public ProductEntity setStatus(String status) {
    return new ProductEntity(id, name, slug, categoryId, price, status, deleted, createdAt, Instant.now());
  }

  public ProductEntity softDelete() {
    return new ProductEntity(id, name, slug, categoryId, price, status, true, createdAt, Instant.now());
  }
}
