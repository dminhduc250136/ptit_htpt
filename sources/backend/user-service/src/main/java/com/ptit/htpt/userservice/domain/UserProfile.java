package com.ptit.htpt.userservice.domain;

import java.time.Instant;
import java.util.UUID;

public record UserProfile(
    String id,
    String email,
    String fullName,
    String phone,
    boolean blocked,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static UserProfile create(String email, String fullName, String phone) {
    Instant now = Instant.now();
    return new UserProfile(UUID.randomUUID().toString(), email, fullName, phone, false, false, now, now);
  }

  public UserProfile update(String email, String fullName, String phone) {
    return new UserProfile(id, email, fullName, phone, blocked, deleted, createdAt, Instant.now());
  }

  public UserProfile setBlocked(boolean blocked) {
    return new UserProfile(id, email, fullName, phone, blocked, deleted, createdAt, Instant.now());
  }

  public UserProfile softDelete() {
    return new UserProfile(id, email, fullName, phone, blocked, true, createdAt, Instant.now());
  }
}
