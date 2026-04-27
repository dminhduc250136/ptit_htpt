package com.ptit.htpt.orderservice.repository;

import com.ptit.htpt.orderservice.domain.OrderEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {
  List<OrderEntity> findByUserId(String userId);

  // Phase 9 / Plan 09-02 (D-06): đếm đơn hàng theo status — dùng cho pendingOrders KPI
  long countByStatus(String status);
}
