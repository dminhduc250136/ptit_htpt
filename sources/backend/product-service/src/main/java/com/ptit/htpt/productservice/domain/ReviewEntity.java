package com.ptit.htpt.productservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "reviews", schema = "product_svc")
public class ReviewEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "product_id", nullable = false, length = 36)
  private String productId;

  @Column(name = "user_id", nullable = false, length = 36)
  private String userId;

  @Column(name = "reviewer_name", nullable = false, length = 150)
  private String reviewerName;

  @Column(nullable = false)
  private int rating;

  @Column(columnDefinition = "TEXT")
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ReviewEntity() { /* JPA */ }

  private ReviewEntity(String id, String productId, String userId, String reviewerName,
                       int rating, String content, Instant now) {
    this.id = id;
    this.productId = productId;
    this.userId = userId;
    this.reviewerName = reviewerName;
    this.rating = rating;
    this.content = content;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public static ReviewEntity create(String productId, String userId, String reviewerName,
                                    int rating, String content) {
    return new ReviewEntity(UUID.randomUUID().toString(),
        productId, userId, reviewerName, rating, content, Instant.now());
  }

  // Record-style accessors
  public String id() { return id; }
  public String productId() { return productId; }
  public String userId() { return userId; }
  public String reviewerName() { return reviewerName; }
  public int rating() { return rating; }
  public String content() { return content; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ReviewEntity that)) return false;
    return Objects.equals(id, that.id);
  }
  @Override public int hashCode() { return Objects.hash(id); }
}
