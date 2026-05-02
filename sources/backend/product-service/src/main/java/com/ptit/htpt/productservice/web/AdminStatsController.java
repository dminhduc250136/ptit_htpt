package com.ptit.htpt.productservice.web;

import com.ptit.htpt.productservice.api.ApiResponse;
import com.ptit.htpt.productservice.service.ProductStatsService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9 / Plan 09-02 (UI-02). Admin-only stats — D-05 REVISED: manual JWT
 * role check (KHÔNG @PreAuthorize). Path /admin/products/stats khớp gateway
 * route `/api/products/admin/stats` → rewrite → `/admin/products/stats`.
 */
@RestController
@RequestMapping("/admin/products")
public class AdminStatsController {

  private final ProductStatsService statsService;
  private final JwtRoleGuard jwtRoleGuard;

  public AdminStatsController(ProductStatsService statsService, JwtRoleGuard jwtRoleGuard) {
    this.statsService = statsService;
    this.jwtRoleGuard = jwtRoleGuard;
  }

  @GetMapping("/stats")
  public ApiResponse<Map<String, Long>> stats(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "Product stats",
        Map.of("totalProducts", statsService.totalProducts()));
  }
}
