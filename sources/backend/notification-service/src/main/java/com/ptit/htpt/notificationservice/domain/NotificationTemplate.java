package com.ptit.htpt.notificationservice.domain;

import java.time.Instant;
import java.util.UUID;

public record NotificationTemplate(
    String id,
    String code,
    String title,
    String body,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static NotificationTemplate create(String code, String title, String body) {
    Instant now = Instant.now();
    return new NotificationTemplate(UUID.randomUUID().toString(), code, title, body, false, now, now);
  }

  public NotificationTemplate update(String code, String title, String body) {
    return new NotificationTemplate(id, code, title, body, deleted, createdAt, Instant.now());
  }

  public NotificationTemplate softDelete() {
    return new NotificationTemplate(id, code, title, body, true, createdAt, Instant.now());
  }
}
