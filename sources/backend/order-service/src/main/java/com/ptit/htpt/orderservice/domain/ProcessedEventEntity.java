package com.ptit.htpt.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Phase 26 / PAY-04 (D-06): lưu eventId đã xử lý để đảm bảo idempotency consumer.
 * Port từ inventory-service, đổi package → com.ptit.htpt.orderservice.
 * Insert bằng native ON CONFLICT DO NOTHING (xem {@link com.ptit.htpt.orderservice.repository.ProcessedEventRepository}).
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

  protected ProcessedEventEntity() {}

  public ProcessedEventEntity(String eventId, String eventType, Instant processedAt) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.processedAt = processedAt;
  }

  public String eventId() { return eventId; }
  public String eventType() { return eventType; }
  public Instant processedAt() { return processedAt; }
}
