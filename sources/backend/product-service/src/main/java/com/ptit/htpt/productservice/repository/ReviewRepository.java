package com.ptit.htpt.productservice.repository;

import com.ptit.htpt.productservice.domain.ReviewEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<ReviewEntity, String>,
                                          JpaSpecificationExecutor<ReviewEntity> {

  // Phase 13 baseline — giữ tạm cho callers cũ chưa migrate (Plan 21-02 sẽ replace).
  Page<ReviewEntity> findByProductIdOrderByCreatedAtDesc(String productId, Pageable pageable);

  boolean existsByProductIdAndUserId(String productId, String userId);

  // Phase 21 — visibility-aware finders (D-07).
  Page<ReviewEntity> findByProductIdAndDeletedAtIsNullAndHiddenFalse(String productId, Pageable pageable);

  boolean existsByProductIdAndUserIdAndDeletedAtIsNull(String productId, String userId);

  Optional<ReviewEntity> findByIdAndDeletedAtIsNull(String reviewId);

  // COALESCE phòng AVG NULL khi không có review (Pitfall 4).
  // Phase 21 (D-08): loại deleted + hidden khỏi recompute scope.
  @Query("SELECT COALESCE(AVG(r.rating), 0), COUNT(r) FROM ReviewEntity r " +
         "WHERE r.productId = :productId AND r.deletedAt IS NULL AND r.hidden = false")
  Object[] computeStats(@Param("productId") String productId);
}
