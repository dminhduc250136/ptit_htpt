package com.ptit.htpt.orderservice.repository;

import com.ptit.htpt.orderservice.domain.OrderEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {
  List<OrderEntity> findByUserId(String userId);

  // Phase 9 / Plan 09-02 (D-06): đếm đơn hàng theo status — dùng cho pendingOrders KPI
  long countByStatus(String status);

  /**
   * Bug fix (orders-api-500): findAll() trả OrderEntity với items LAZY → khi DTO mapper iterate
   * items ngoài transaction (open-in-view=false, service không có @Transactional) → ném
   * LazyInitializationException → 500 INTERNAL_ERROR cho cả /orders (no userId) và /admin/orders.
   * Dùng LEFT JOIN FETCH để eager-load items trong cùng query, giống pattern của
   * findByUserIdWithFilters.
   */
  @Query("SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items ORDER BY o.updatedAt DESC")
  List<OrderEntity> findAllWithItems();

  /**
   * Bug fix (orders-api-500): single-entity fetch với items eager-loaded — tránh lazy init
   * khi GET /orders/{id} hoặc admin endpoints map sang DTO ngoài transaction.
   */
  @Query("SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.id = :id")
  Optional<OrderEntity> findByIdWithItems(@Param("id") String id);

  /**
   * Phase 11 / ACCT-02 (D-12, D-13, D-14): filter orders by userId + optional params.
   * status=null → bỏ qua filter status. from=null / to=null → bỏ qua filter date.
   * q tìm kiếm trên id (LOWER LIKE) — D-13: order ID only, không join items.
   */
  @Query("SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.userId = :userId " +
         "AND (cast(:status as string) IS NULL OR o.status = :status) " +
         "AND (cast(:from as timestamp) IS NULL OR o.createdAt >= :from) " +
         "AND (cast(:to as timestamp) IS NULL OR o.createdAt <= :to) " +
         "AND (cast(:q as string) IS NULL OR LOWER(o.id) LIKE LOWER(CONCAT('%', cast(:q as string), '%'))) " +
         "ORDER BY o.createdAt DESC")
  List<OrderEntity> findByUserIdWithFilters(
      @Param("userId") String userId,
      @Param("status") String status,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("q") String q
  );

  /**
   * Phase 13 / Plan 02 (D-01, D-02): Check buyer eligibility — user đã có order DELIVERED
   * chứa productId chưa. Dùng cho internal endpoint check từ product-svc.
   *
   * <p>Hardcode status='DELIVERED' theo D-02 lock (không nhận status làm param).
   * COUNT > 0 thay vì EXISTS vì JPQL không support EXISTS subquery độc lập trên Hibernate 6.
   */
  @Query("SELECT COUNT(o) > 0 FROM OrderEntity o JOIN o.items i " +
         "WHERE o.userId = :userId AND o.status = 'DELIVERED' AND i.productId = :productId")
  boolean existsDeliveredOrderWithProduct(
      @Param("userId") String userId,
      @Param("productId") String productId);
}
