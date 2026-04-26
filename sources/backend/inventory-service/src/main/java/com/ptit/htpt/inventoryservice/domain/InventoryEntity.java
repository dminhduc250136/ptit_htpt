package com.ptit.htpt.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity cho inventory_svc.inventory_items. Renamed từ record cũ {@code InventoryItem}
 * theo PATTERNS.md cross-cutting note #1 (symmetry với UserEntity/ProductEntity).
 *
 * <p>Phase 5 scope-cut: KHÔNG có soft-delete (record cũ có cờ {@code deleted} nhưng plan V1 DDL
 * loại bỏ vì inventory không cần audit ẩn). Phase 8 sẽ thêm reservation flow + stock decrement.
 */
@Entity
@Table(name = "inventory_items", schema = "inventory_svc")
public class InventoryEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "product_id", length = 36, nullable = false, unique = true)
  private String productId;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false)
  private int reserved;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // JPA proxy: protected no-arg constructor
  protected InventoryEntity() {}

  protected InventoryEntity(String id, String productId, int quantity, int reserved,
                            Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.productId = productId;
    this.quantity = quantity;
    this.reserved = reserved;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static InventoryEntity create(String productId, int quantity, int reserved) {
    Instant now = Instant.now();
    return new InventoryEntity(UUID.randomUUID().toString(), productId, quantity, reserved, now, now);
  }

  public void update(int quantity, int reserved) {
    this.quantity = quantity;
    this.reserved = reserved;
    this.updatedAt = Instant.now();
  }

  public void adjustQuantity(int quantity) {
    this.quantity = quantity;
    this.updatedAt = Instant.now();
  }

  // Getters: keep record-style accessor names cho service layer compatibility
  public String id() { return id; }
  public String productId() { return productId; }
  public int quantity() { return quantity; }
  public int reserved() { return reserved; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof InventoryEntity that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
