package com.ptit.htpt.productservice.repository;

import com.ptit.htpt.productservice.domain.ReviewEntity;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specification factory cho admin filter 4-state ({@code all|visible|hidden|deleted}) — D-15.
 *
 * <p>Áp dụng qua {@link ReviewRepository#findAll(Specification, org.springframework.data.domain.Pageable)}
 * (kế thừa từ {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}).
 */
public final class AdminReviewSpecifications {
  private AdminReviewSpecifications() {}

  public static Specification<ReviewEntity> withFilter(String filter) {
    return (root, query, cb) -> switch (filter == null ? "all" : filter) {
      case "visible" -> cb.and(cb.isNull(root.get("deletedAt")), cb.isFalse(root.get("hidden")));
      case "hidden"  -> cb.isTrue(root.get("hidden"));
      case "deleted" -> cb.isNotNull(root.get("deletedAt"));
      default        -> cb.conjunction(); // "all" — không thêm predicate
    };
  }
}
