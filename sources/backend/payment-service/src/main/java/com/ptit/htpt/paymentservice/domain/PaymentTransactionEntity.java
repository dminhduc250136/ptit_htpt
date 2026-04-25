package com.ptit.htpt.paymentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "payments", schema = "payment_svc")
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE payment_svc.payments SET deleted = true, updated_at = NOW() WHERE id = ?")
public class PaymentTransactionEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "session_id", length = 120)
  private String sessionId;

  @Column(length = 120)
  private String reference;

  @Column(length = 500)
  private String message;

  @Column(precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(length = 50)
  private String method;

  @Column(length = 30, nullable = false)
  private String status;

  @Column(nullable = false)
  private boolean deleted = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // JPA proxy
  protected PaymentTransactionEntity() {}

  protected PaymentTransactionEntity(String id, String sessionId, String reference, String message,
                                     BigDecimal amount, String method, String status,
                                     boolean deleted, Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.sessionId = sessionId;
    this.reference = reference;
    this.message = message;
    this.amount = amount;
    this.method = method;
    this.status = status;
    this.deleted = deleted;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static PaymentTransactionEntity create(
      String sessionId,
      String reference,
      BigDecimal amount,
      String method,
      String status,
      String message
  ) {
    Instant now = Instant.now();
    return new PaymentTransactionEntity(
        UUID.randomUUID().toString(), sessionId, reference, message,
        amount, method, status, false, now, now);
  }

  public void update(String sessionId, String reference, BigDecimal amount, String method,
                     String status, String message) {
    this.sessionId = sessionId;
    this.reference = reference;
    this.amount = amount;
    this.method = method;
    this.status = status;
    this.message = message;
    this.updatedAt = Instant.now();
  }

  public void setStatus(String status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public void softDelete() {
    this.deleted = true;
    this.updatedAt = Instant.now();
  }

  public String id() { return id; }
  public String sessionId() { return sessionId; }
  public String reference() { return reference; }
  public String message() { return message; }
  public BigDecimal amount() { return amount; }
  public String method() { return method; }
  public String status() { return status; }
  public boolean deleted() { return deleted; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PaymentTransactionEntity that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
