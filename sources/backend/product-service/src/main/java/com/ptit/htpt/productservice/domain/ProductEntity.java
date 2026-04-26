package com.ptit.htpt.productservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA @Entity cho bảng product_svc.products.
 *
 * <p>Dùng accessor naming dạng record (`name()`, `slug()`, ...) để service layer
 * không phải đổi gọi khi migrate từ in-memory record cũ.
 *
 * <p>Soft-delete qua @SQLRestriction + @SQLDelete: findAll/findById tự động loại
 * record có deleted=true; delete(entity) sẽ trigger UPDATE thay vì DELETE.
 */
@Entity
@Table(name = "products", schema = "product_svc")
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE product_svc.products SET deleted = true, updated_at = NOW() WHERE id = ?")
public class ProductEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(nullable = false, length = 300)
  private String name;

  @Column(nullable = false, length = 320, unique = true)
  private String slug;

  @Column(name = "category_id", nullable = false, length = 36)
  private String categoryId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(length = 200)
  private String brand;

  @Column(name = "thumbnail_url", length = 500)
  private String thumbnailUrl;

  @Column(name = "short_description", length = 500)
  private String shortDescription;

  @Column(name = "original_price", precision = 12, scale = 2)
  private BigDecimal originalPrice;

  @Column(nullable = false)
  private int stock = 0;

  @Column(nullable = false)
  private boolean deleted = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA proxy: protected no-arg constructor (Pitfall 1, RESEARCH §Risks). */
  protected ProductEntity() {}

  protected ProductEntity(String id, String name, String slug, String categoryId,
                          BigDecimal price, String status, boolean deleted,
                          Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.name = name;
    this.slug = slug;
    this.categoryId = categoryId;
    this.price = price;
    this.status = status;
    this.deleted = deleted;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static ProductEntity create(String name, String slug, String categoryId,
                                     BigDecimal price, String status,
                                     String brand, String thumbnailUrl,
                                     String shortDescription, BigDecimal originalPrice) {
    Instant now = Instant.now();
    ProductEntity entity = new ProductEntity(UUID.randomUUID().toString(), name, slug, categoryId,
        price, status, false, now, now);
    entity.brand = brand;
    entity.thumbnailUrl = thumbnailUrl;
    entity.shortDescription = shortDescription;
    entity.originalPrice = originalPrice;
    return entity;
  }

  public void update(String name, String slug, String categoryId, BigDecimal price, String status,
                     String brand, String thumbnailUrl,
                     String shortDescription, BigDecimal originalPrice, int stock) {
    this.name = name;
    this.slug = slug;
    this.categoryId = categoryId;
    this.price = price;
    this.status = status;
    this.brand = brand;
    this.thumbnailUrl = thumbnailUrl;
    this.shortDescription = shortDescription;
    this.originalPrice = originalPrice;
    this.stock = stock;
    this.updatedAt = Instant.now();
  }

  public void setStatus(String status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public void setStock(int stock) {
    this.stock = Math.max(0, stock);
    this.updatedAt = Instant.now();
  }

  public void softDelete() {
    this.deleted = true;
    this.updatedAt = Instant.now();
  }

  public String id() { return id; }
  public String name() { return name; }
  public String slug() { return slug; }
  public String categoryId() { return categoryId; }
  public BigDecimal price() { return price; }
  public String status() { return status; }
  public String brand() { return brand; }
  public String thumbnailUrl() { return thumbnailUrl; }
  public String shortDescription() { return shortDescription; }
  public BigDecimal originalPrice() { return originalPrice; }
  public int stock() { return stock; }
  public boolean deleted() { return deleted; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  /** equals/hashCode by id only (Pitfall 2 — Hibernate proxy / pre-persist safety). */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProductEntity that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
