package com.ptit.htpt.notificationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * D-06: idempotency table cho consumer notification-service.
 * PK event_id đảm bảo INSERT ... ON CONFLICT DO NOTHING atomic — duplicate sẽ bị bỏ qua.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

  @Id
  @Column(name = "event_id", length = 36, nullable = false, updatable = false)
  private String eventId;

  @Column(name = "event_type", length = 64, nullable = false)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected ProcessedEventEntity() {
    // JPA
  }

  public ProcessedEventEntity(String eventId, String eventType, Instant processedAt) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.processedAt = processedAt;
  }

  public String eventId() {
    return eventId;
  }

  public String eventType() {
    return eventType;
  }

  public Instant processedAt() {
    return processedAt;
  }
}
