package com.ptit.htpt.notificationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * D-14: log mỗi lần consumer xử lý OrderPlaced + render template + (giả lập) gửi notification.
 * KHÔNG gửi SMTP thật — chỉ ghi dispatch_log với status=SENT, channel=email.
 *
 * <p>Match đúng 10 cột trong V1__init_schema.sql (Plan 23-02):
 *   id, event_id, recipient_user_id, channel, subject, body, status, sent_at, created_at, updated_at.
 */
@Entity
@Table(name = "dispatch_log")
public class DispatchLogEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "event_id", length = 36, nullable = false)
  private String eventId;

  @Column(name = "recipient_user_id", length = 36, nullable = false)
  private String recipientUserId;

  @Column(length = 16, nullable = false)
  private String channel;

  @Column(length = 255, nullable = false)
  private String subject;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(length = 16, nullable = false)
  private String status;

  @Column(name = "sent_at", nullable = false)
  private Instant sentAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected DispatchLogEntity() {
    // JPA
  }

  public DispatchLogEntity(String id, String eventId, String recipientUserId, String channel,
                            String subject, String body, String status,
                            Instant sentAt, Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.eventId = eventId;
    this.recipientUserId = recipientUserId;
    this.channel = channel;
    this.subject = subject;
    this.body = body;
    this.status = status;
    this.sentAt = sentAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static DispatchLogEntity create(String eventId, String recipientUserId, String channel,
                                          String subject, String body, String status) {
    Instant now = Instant.now();
    return new DispatchLogEntity(
        UUID.randomUUID().toString(),
        eventId,
        recipientUserId,
        channel,
        subject,
        body,
        status,
        now,
        now,
        now
    );
  }

  public String id() {
    return id;
  }

  public String eventId() {
    return eventId;
  }

  public String recipientUserId() {
    return recipientUserId;
  }

  public String channel() {
    return channel;
  }

  public String subject() {
    return subject;
  }

  public String body() {
    return body;
  }

  public String status() {
    return status;
  }

  public Instant sentAt() {
    return sentAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
