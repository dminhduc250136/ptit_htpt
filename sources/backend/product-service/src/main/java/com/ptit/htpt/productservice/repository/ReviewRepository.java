package com.ptit.htpt.productservice.repository;

import com.ptit.htpt.productservice.domain.ReviewEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<ReviewEntity, String> {

  Page<ReviewEntity> findByProductIdOrderByCreatedAtDesc(String productId, Pageable pageable);

  boolean existsByProductIdAndUserId(String productId, String userId);

  // COALESCE phòng AVG NULL khi không có review (Pitfall 4)
  @Query("SELECT COALESCE(AVG(r.rating), 0), COUNT(r) FROM ReviewEntity r WHERE r.productId = :productId")
  Object[] computeStats(@Param("productId") String productId);
}
