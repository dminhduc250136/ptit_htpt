package com.ptit.htpt.productservice.domain;

import java.time.Instant;
import java.util.UUID;

public record CategoryEntity(
    String id,
    String name,
    String parentId,
    String status,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static CategoryEntity create(String name, String parentId, String status) {
    Instant now = Instant.now();
    return new CategoryEntity(UUID.randomUUID().toString(), name, parentId, status, false, now, now);
  }

  public CategoryEntity update(String name, String parentId, String status) {
    return new CategoryEntity(id, name, parentId, status, deleted, createdAt, Instant.now());
  }

  public CategoryEntity softDelete() {
    return new CategoryEntity(id, name, parentId, status, true, createdAt, Instant.now());
  }
}
