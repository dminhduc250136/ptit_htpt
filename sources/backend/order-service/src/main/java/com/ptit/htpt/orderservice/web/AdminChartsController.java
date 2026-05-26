package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.api.ApiResponse;
import com.ptit.htpt.orderservice.service.OrderChartsService;
import com.ptit.htpt.orderservice.service.OrderChartsService.RevenuePoint;
import com.ptit.htpt.orderservice.service.OrderChartsService.StatusPoint;
import com.ptit.htpt.orderservice.service.OrderChartsService.TopProductPoint;
import com.ptit.htpt.orderservice.service.Range;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 19 / Plan 19-01 (D-01, D-02, ADMIN-01..03): 3 admin chart endpoints.
 *
 * <p>Path {@code /admin/orders/charts/...} khớp gateway rewrite
 * {@code /api/orders/admin/(?<seg>.*) → /admin/orders/${seg}} (existing route).
 *
 * <p>Auth gate: {@link JwtRoleGuard#requireAdmin(String)} per endpoint —
 * missing/invalid Bearer → 401, non-ADMIN → 403, invalid range → 400 (Range.parse).
 */
@RestController
@RequestMapping("/admin/orders/charts")
public class AdminChartsController {

  private final OrderChartsService chartsService;
  private final JwtRoleGuard jwtRoleGuard;

  public AdminChartsController(OrderChartsService chartsService, JwtRoleGuard jwtRoleGuard) {
    this.chartsService = chartsService;
    this.jwtRoleGuard = jwtRoleGuard;
  }

  @GetMapping("/revenue")
  public ApiResponse<List<RevenuePoint>> revenue(
      @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "Revenue chart",
        chartsService.revenueByDay(Range.parse(range)));
  }

  @GetMapping("/top-products")
  public ApiResponse<List<TopProductPoint>> topProducts(
      @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
    jwtRoleGuard.requireAdmin(authHeader);
    // D-03 + Pitfall #4: forward authHeader xuống service → ProductBatchClient
    return ApiResponse.of(200, "Top products",
        chartsService.topProducts(Range.parse(range), authHeader));
  }

  @GetMapping("/status-distribution")
  public ApiResponse<List<StatusPoint>> statusDistribution(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "Order status distribution",
        chartsService.statusDistribution());
  }
}
