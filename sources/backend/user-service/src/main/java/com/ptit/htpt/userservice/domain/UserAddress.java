package com.ptit.htpt.userservice.domain;

import java.time.Instant;
import java.util.UUID;

public record UserAddress(
    String id,
    String userId,
    String label,
    String addressLine,
    String city,
    boolean defaultAddress,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static UserAddress create(String userId, String label, String addressLine, String city, boolean defaultAddress) {
    Instant now = Instant.now();
    return new UserAddress(UUID.randomUUID().toString(), userId, label, addressLine, city, defaultAddress, false, now, now);
  }

  public UserAddress update(String userId, String label, String addressLine, String city, boolean defaultAddress) {
    return new UserAddress(id, userId, label, addressLine, city, defaultAddress, deleted, createdAt, Instant.now());
  }

  public UserAddress softDelete() {
    return new UserAddress(id, userId, label, addressLine, city, defaultAddress, true, createdAt, Instant.now());
  }
}
