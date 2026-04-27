package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.api.ApiResponse;
import com.ptit.htpt.orderservice.repository.OrderRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 13 / Plan 02 (D-01, D-02): Internal endpoint cho product-svc check buyer eligibility.
 * KHÔNG expose qua api-gateway — chỉ accessible trên Docker network (T-13-02-01).
 *
 * <p>Không có authentication: Docker network isolation là security boundary theo thiết kế.
 * Verify: grep '/internal/' api-gateway/application.yml = 0.
 */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

  private final OrderRepository orderRepository;

  public InternalOrderController(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  /**
   * GET /internal/orders/eligibility?userId=X&productId=Y
   *
   * <p>Trả {@code {eligible: true}} nếu user có ít nhất 1 order DELIVERED chứa productId,
   * ngược lại trả {@code {eligible: false}}.
   */
  @GetMapping("/eligibility")
  public ApiResponse<Map<String, Boolean>> checkEligibility(
      @RequestParam String userId,
      @RequestParam String productId) {
    boolean eligible = orderRepository.existsDeliveredOrderWithProduct(userId, productId);
    return ApiResponse.of(200, "Eligibility checked", Map.of("eligible", eligible));
  }
}
