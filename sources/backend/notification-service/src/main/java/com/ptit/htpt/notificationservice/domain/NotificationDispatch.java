package com.ptit.htpt.notificationservice.domain;

import java.time.Instant;
import java.util.UUID;

public record NotificationDispatch(
    String id,
    String templateId,
    String recipient,
    String status,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static NotificationDispatch create(String templateId, String recipient, String status) {
    Instant now = Instant.now();
    return new NotificationDispatch(UUID.randomUUID().toString(), templateId, recipient, status, false, now, now);
  }

  public NotificationDispatch update(String templateId, String recipient, String status) {
    return new NotificationDispatch(id, templateId, recipient, status, deleted, createdAt, Instant.now());
  }

  public NotificationDispatch softDelete() {
    return new NotificationDispatch(id, templateId, recipient, status, true, createdAt, Instant.now());
  }
}
