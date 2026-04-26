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
@Table(name = "payment_sessions", schema = "payment_svc")
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE payment_svc.payment_sessions SET deleted = true, updated_at = NOW() WHERE id = ?")
public class PaymentSessionEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "order_id", length = 36, nullable = false)
  private String orderId;

  @Column(length = 50, nullable = false)
  private String provider;

  @Column(precision = 12, scale = 2, nullable = false)
  private BigDecimal amount;

  @Column(length = 30, nullable = false)
  private String status;

  @Column(nullable = false)
  private boolean deleted = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PaymentSessionEntity() {}

  protected PaymentSessionEntity(String id, String orderId, String provider, BigDecimal amount,
                                 String status, boolean deleted, Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.orderId = orderId;
    this.provider = provider;
    this.amount = amount;
    this.status = status;
    this.deleted = deleted;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static PaymentSessionEntity create(String orderId, String provider, BigDecimal amount, String status) {
    Instant now = Instant.now();
    return new PaymentSessionEntity(UUID.randomUUID().toString(), orderId, provider, amount, status, false, now, now);
  }

  public void update(String orderId, String provider, BigDecimal amount, String status) {
    this.orderId = orderId;
    this.provider = provider;
    this.amount = amount;
    this.status = status;
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
  public String orderId() { return orderId; }
  public String provider() { return provider; }
  public BigDecimal amount() { return amount; }
  public String status() { return status; }
  public boolean deleted() { return deleted; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PaymentSessionEntity that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
