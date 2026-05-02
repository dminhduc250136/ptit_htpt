package com.ptit.htpt.orderservice.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

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

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  private List<OrderItemEntity> items = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "shipping_address", columnDefinition = "jsonb")
  private String shippingAddress;

  @Column(name = "payment_method", length = 30)
  private String paymentMethod;

  // Phase 20 / COUP-01 (D-02): snapshot 2 cột coupon trên orders cho display nhanh
  // (KHÔNG join coupon_redemptions). Bảo toàn lịch sử nếu coupon bị xoá sau này.
  // Default 0 (DEFAULT 0 trong V5 ALTER) — backward compatible với order cũ.
  @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal discountAmount = BigDecimal.ZERO;

  @Column(name = "coupon_code", length = 64)
  private String couponCode;

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
  public List<OrderItemEntity> items() { return items; }
  public String shippingAddress() { return shippingAddress; }
  public String paymentMethod() { return paymentMethod; }

  // Phase 20 / COUP-01 (D-02): snapshot accessors. Set qua setter trong
  // OrderCrudService.create sau khi atomic redeem (Plan 20-03).
  public BigDecimal discountAmount() { return discountAmount; }
  public String couponCode() { return couponCode; }
  public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
  public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

  public void addItem(OrderItemEntity item) {
    this.items.add(item);
  }

  public void setShippingAddress(String shippingAddress) {
    this.shippingAddress = shippingAddress;
  }

  public void setPaymentMethod(String paymentMethod) {
    this.paymentMethod = paymentMethod;
  }

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
