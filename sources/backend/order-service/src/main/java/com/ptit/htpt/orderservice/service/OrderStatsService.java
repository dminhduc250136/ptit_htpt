package com.ptit.htpt.orderservice.service;

import com.ptit.htpt.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 9 / Plan 09-02 (UI-02): Admin stats service cho order-service.
 * Cung cấp số liệu thật cho admin dashboard KPI cards.
 */
@Service
public class OrderStatsService {

  private final OrderRepository orderRepo;

  public OrderStatsService(OrderRepository orderRepo) {
    this.orderRepo = orderRepo;
  }

  @Transactional(readOnly = true)
  public long totalOrders() {
    return orderRepo.count();
  }

  /**
   * D-06: pending = status = "PENDING" ONLY (không gộp SHIPPING/PAID).
   * Method derived query — Spring Data JPA tự generate từ tên field `status`.
   * NOTE: OrderEntity field là `status` (KHÔNG phải `orderStatus`).
   */
  @Transactional(readOnly = true)
  public long pendingOrders() {
    return orderRepo.countByStatus("PENDING");
  }
}
