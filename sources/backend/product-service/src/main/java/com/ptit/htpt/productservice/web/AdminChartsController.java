package com.ptit.htpt.productservice.web;

import com.ptit.htpt.productservice.api.ApiResponse;
import com.ptit.htpt.productservice.service.LowStockService;
import com.ptit.htpt.productservice.service.LowStockService.LowStockItem;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 19 / Plan 03 (ADMIN-05). Admin-only low-stock chart endpoint.
 *
 * <p>D-02: tái dùng JwtRoleGuard.requireAdmin manual check (KHÔNG @PreAuthorize).
 * Path /admin/products/charts khớp gateway rewrite `/api/products/admin/charts/**`
 * → `/admin/products/charts/**` qua existing product-service-admin route.
 *
 * <p>D-09: empty list trả [] (không 404), FE render placeholder.
 */
@RestController
@RequestMapping("/admin/products/charts")
public class AdminChartsController {

  private final LowStockService lowStockService;
  private final JwtRoleGuard jwtRoleGuard;

  public AdminChartsController(LowStockService lowStockService, JwtRoleGuard jwtRoleGuard) {
    this.lowStockService = lowStockService;
    this.jwtRoleGuard = jwtRoleGuard;
  }

  @GetMapping("/low-stock")
  public ApiResponse<List<LowStockItem>> lowStock(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "Low stock products", lowStockService.list());
  }
}
