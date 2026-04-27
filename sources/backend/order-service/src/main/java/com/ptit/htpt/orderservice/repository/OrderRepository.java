package com.ptit.htpt.orderservice.repository;

import com.ptit.htpt.orderservice.domain.OrderEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {
  List<OrderEntity> findByUserId(String userId);

  // Phase 9 / Plan 09-02 (D-06): đếm đơn hàng theo status — dùng cho pendingOrders KPI
  long countByStatus(String status);

  /**
   * Phase 11 / ACCT-02 (D-12, D-13, D-14): filter orders by userId + optional params.
   * status=null → bỏ qua filter status. from=null / to=null → bỏ qua filter date.
   * q tìm kiếm trên id (LOWER LIKE) — D-13: order ID only, không join items.
   */
  @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId " +
         "AND (:status IS NULL OR o.status = :status) " +
         "AND (:from IS NULL OR o.createdAt >= :from) " +
         "AND (:to IS NULL OR o.createdAt <= :to) " +
         "AND (:q IS NULL OR LOWER(o.id) LIKE LOWER(CONCAT('%', :q, '%'))) " +
         "ORDER BY o.createdAt DESC")
  List<OrderEntity> findByUserIdWithFilters(
      @Param("userId") String userId,
      @Param("status") String status,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("q") String q
  );
}
