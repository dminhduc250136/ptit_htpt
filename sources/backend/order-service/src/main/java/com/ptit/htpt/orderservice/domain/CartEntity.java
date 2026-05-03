package com.ptit.htpt.orderservice.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts", schema = "order_svc")
public class CartEntity {
  @Id @Column(length = 36, nullable = false, updatable = false) private String id;
  @Column(name = "user_id", length = 36, nullable = false, unique = true) private String userId;

  @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  private List<CartItemEntity> items = new ArrayList<>();

  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  protected CartEntity() {}

  public static CartEntity create(String userId) {
    CartEntity c = new CartEntity();
    c.id = UUID.randomUUID().toString();
    c.userId = userId;
    c.createdAt = Instant.now();
    c.updatedAt = Instant.now();
    return c;
  }

  public void addItem(CartItemEntity item) {
    items.add(item);
    item.setCart(this);
    this.updatedAt = Instant.now();
  }

  public void removeItem(CartItemEntity item) {
    items.remove(item);
    item.setCart(null);
    this.updatedAt = Instant.now();
  }

  public void touch() { this.updatedAt = Instant.now(); }

  public String id() { return id; }
  public String userId() { return userId; }
  public List<CartItemEntity> items() { return items; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }
}
