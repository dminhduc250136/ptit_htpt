package com.ptit.htpt.productservice.web;

import com.ptit.htpt.productservice.api.ApiResponse;
import com.ptit.htpt.productservice.service.ProductBatchService;
import com.ptit.htpt.productservice.service.ProductBatchService.ProductSummary;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 19 / Plan 03 (D-03 cross-svc enrichment helper). POST /admin/products/batch.
 *
 * <p>Dùng bởi order-svc ProductBatchClient (Plan 19-01) để enrich top-products chart
 * với name/brand/thumbnailUrl.
 *
 * <p>Path /admin/products/batch (KHÔNG /admin/products/charts/batch) — gateway
 * `/api/products/admin/batch` rewrite → `/admin/products/batch` qua existing
 * product-service-admin catch-all route.
 *
 * <p>Body shape: `{"ids": ["uuid1", "uuid2", ...]}`. Defensive: null body / missing
 * "ids" key → empty list (service tự handle empty).
 */
@RestController
@RequestMapping("/admin/products")
public class AdminProductBatchController {

  private final ProductBatchService productBatchService;
  private final JwtRoleGuard jwtRoleGuard;

  public AdminProductBatchController(ProductBatchService productBatchService,
                                     JwtRoleGuard jwtRoleGuard) {
    this.productBatchService = productBatchService;
    this.jwtRoleGuard = jwtRoleGuard;
  }

  @PostMapping("/batch")
  public ApiResponse<List<ProductSummary>> batch(
      @RequestBody(required = false) Map<String, List<String>> body,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
    jwtRoleGuard.requireAdmin(authHeader);
    List<String> ids = body == null ? List.of() : body.getOrDefault("ids", List.of());
    return ApiResponse.of(200, "Product batch", productBatchService.findByIds(ids));
  }
}
