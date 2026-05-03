package com.ptit.htpt.productservice.service;

import com.ptit.htpt.productservice.domain.ReviewEntity;
import java.time.Instant;

/**
 * Wire format admin moderation list (REV-06). {@code productSlug} có thể null nếu
 * sản phẩm tham chiếu đã soft-delete (@SQLRestriction trên ProductEntity skip lookup) —
 * FE render "—" khi null.
 */
public record AdminReviewDTO(
    String id,
    String productId,
    String productSlug,
    String userId,
    String reviewerName,
    int rating,
    String content,
    boolean hidden,
    Instant deletedAt,
    Instant createdAt
) {
  public static AdminReviewDTO from(ReviewEntity r, String productSlug) {
    return new AdminReviewDTO(
        r.id(), r.productId(), productSlug,
        r.userId(), r.reviewerName(),
        r.rating(), r.content(),
        r.hidden(), r.deletedAt(), r.createdAt()
    );
  }
}
