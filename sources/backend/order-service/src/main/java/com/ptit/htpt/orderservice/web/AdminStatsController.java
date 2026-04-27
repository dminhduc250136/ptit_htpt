package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.api.ApiResponse;
import com.ptit.htpt.orderservice.service.OrderStatsService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9 / Plan 09-02 (UI-02). Admin-only stats — D-05 REVISED: manual JWT
 * role check (KHÔNG @PreAuthorize). Path /admin/orders/stats khớp gateway
 * route `/api/orders/admin/stats` → rewrite → `/admin/orders/stats`.
 */
@RestController
@RequestMapping("/admin/orders")
public class AdminStatsController {

  private final OrderStatsService statsService;
  private final JwtRoleGuard jwtRoleGuard;

  public AdminStatsController(OrderStatsService statsService, JwtRoleGuard jwtRoleGuard) {
    this.statsService = statsService;
    this.jwtRoleGuard = jwtRoleGuard;
  }

  /**
   * D-04: response shape `{totalOrders, pendingOrders}`.
   * D-06: pendingOrders = status = "PENDING" ONLY.
   */
  @GetMapping("/stats")
  public ApiResponse<Map<String, Long>> stats(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "Order stats", Map.of(
        "totalOrders", statsService.totalOrders(),
        "pendingOrders", statsService.pendingOrders()
    ));
  }
}
