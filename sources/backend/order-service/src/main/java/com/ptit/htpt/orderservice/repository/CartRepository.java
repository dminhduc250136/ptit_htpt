package com.ptit.htpt.orderservice.repository;

import com.ptit.htpt.orderservice.domain.CartEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<CartEntity, String> {

  /**
   * Lookup cart by user_id (UNIQUE constraint guarantees le 1 row).
   * EntityGraph "items" de fetch-join tranh LazyInitializationException khi
   * mapper iterate items ngoai transaction (cung pattern Phase 8 findByIdWithItems).
   */
  @EntityGraph(attributePaths = "items")
  Optional<CartEntity> findByUserId(@Param("userId") String userId);
}
