package com.ptit.htpt.orderservice.domain;

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
@Table(name = "orders", schema = "order_svc")
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE order_svc.orders SET deleted = true, updated_at = NOW() WHERE id = ?")
public class OrderEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "user_id", nullable = false, length = 36)
  private String userId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal total;

  @Column(nullable = false, length = 30)
  private String status;

  // Cross-cutting note #3 (PATTERNS.md): preserve `note` field từ record cũ — nullable
  @Column(length = 500)
  private String note;

  @Column(nullable = false)
  private boolean deleted = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // JPA proxy: protected no-arg constructor
  protected OrderEntity() {}

  protected OrderEntity(String id, String userId, BigDecimal total, String status,
                        String note, boolean deleted, Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.userId = userId;
    this.total = total;
    this.status = status;
    this.note = note;
    this.deleted = deleted;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static OrderEntity create(String userId, BigDecimal total, String status, String note) {
    Instant now = Instant.now();
    return new OrderEntity(UUID.randomUUID().toString(), userId, total, status, note, false, now, now);
  }

  public void update(String userId, BigDecimal total, String status, String note) {
    this.userId = userId;
    this.total = total;
    this.status = status;
    this.note = note;
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

  // Getters: giữ tên giống record cũ (`name()` style) để service layer không phải đổi nhiều
  public String id() { return id; }
  public String userId() { return userId; }
  public BigDecimal total() { return total; }
  /** Alias cho compat code cũ dùng `totalAmount()`. */
  public BigDecimal totalAmount() { return total; }
  public String status() { return status; }
  public String note() { return note; }
  public boolean deleted() { return deleted; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OrderEntity that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
