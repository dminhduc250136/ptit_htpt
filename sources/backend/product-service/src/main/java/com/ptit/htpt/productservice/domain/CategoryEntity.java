package com.ptit.htpt.productservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA @Entity cho bảng product_svc.categories.
 *
 * <p>Phase 5 schema: id, name, slug, deleted, createdAt, updatedAt.
 * Field {@code parentId, status} của record cũ ĐÃ DROP (xem 05-03-SUMMARY §Deviations).
 */
@Entity
@Table(name = "categories", schema = "product_svc")
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE product_svc.categories SET deleted = true, updated_at = NOW() WHERE id = ?")
public class CategoryEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, length = 220, unique = true)
  private String slug;

  @Column(nullable = false)
  private boolean deleted = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CategoryEntity() {}

  protected CategoryEntity(String id, String name, String slug, boolean deleted,
                           Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.name = name;
    this.slug = slug;
    this.deleted = deleted;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static CategoryEntity create(String name, String slug) {
    Instant now = Instant.now();
    return new CategoryEntity(UUID.randomUUID().toString(), name, slug, false, now, now);
  }

  public void update(String name, String slug) {
    this.name = name;
    this.slug = slug;
    this.updatedAt = Instant.now();
  }

  public void softDelete() {
    this.deleted = true;
    this.updatedAt = Instant.now();
  }

  public String id() { return id; }
  public String name() { return name; }
  public String slug() { return slug; }
  public boolean deleted() { return deleted; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CategoryEntity that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
