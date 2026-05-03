package com.ptit.htpt.orderservice.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_items", schema = "order_svc",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "product_id"}))
public class CartItemEntity {
  @Id @Column(length = 36, nullable = false, updatable = false) private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cart_id", nullable = false)
  private CartEntity cart;

  @Column(name = "product_id", nullable = false, length = 36) private String productId;
  @Column(nullable = false) private int quantity;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  protected CartItemEntity() {}

  public static CartItemEntity create(CartEntity cart, String productId, int quantity) {
    CartItemEntity i = new CartItemEntity();
    i.id = UUID.randomUUID().toString();
    i.cart = cart;
    i.productId = productId;
    i.quantity = quantity;
    i.createdAt = Instant.now();
    i.updatedAt = Instant.now();
    return i;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
    this.updatedAt = Instant.now();
  }

  public void setCart(CartEntity cart) { this.cart = cart; }

  public String id() { return id; }
  public String productId() { return productId; }
  public int quantity() { return quantity; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  // equals/hashCode based on id only — tránh LazyInit infinite loop trên collection
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CartItemEntity that)) return false;
    return id != null && id.equals(that.id);
  }
  @Override public int hashCode() { return id == null ? 0 : id.hashCode(); }
}
