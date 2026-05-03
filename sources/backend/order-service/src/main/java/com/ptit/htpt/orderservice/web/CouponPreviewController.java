package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.api.ApiResponse;
import com.ptit.htpt.orderservice.service.CouponPreviewService;
import com.ptit.htpt.orderservice.web.CouponDtos.CouponPreviewRequest;
import com.ptit.htpt.orderservice.web.CouponDtos.CouponPreviewResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 20 / Plan 20-03 (D-13): User-side preview validation endpoint.
 *
 * <p>POST /orders/coupons/validate body {@link CouponPreviewRequest} →
 * {@link CouponPreviewResponse}. Header {@code X-User-Id} optional — nếu có,
 * {@link CouponPreviewService#validate(String, java.math.BigDecimal, String)}
 * sẽ check user-already-redeemed; nếu null sẽ skip check (anonymous preview).
 *
 * <p>KHÔNG gate JwtRoleGuard — bất kỳ user nào cũng có thể preview (read-only).
 * Rate-limit defer (T-20-03-03 — accepted).
 *
 * <p>Gateway Plan 20-04 rewrite {@code /api/orders/coupons/validate} → {@code /orders/coupons/validate}.
 */
@RestController
@RequestMapping("/orders/coupons")
public class CouponPreviewController {

  private final CouponPreviewService couponPreviewService;

  public CouponPreviewController(CouponPreviewService couponPreviewService) {
    this.couponPreviewService = couponPreviewService;
  }

  @PostMapping("/validate")
  public ApiResponse<CouponPreviewResponse> validate(
      @Valid @RequestBody CouponPreviewRequest req,
      @RequestHeader(value = "X-User-Id", required = false) String userId) {
    return ApiResponse.of(200, "Coupon preview",
        couponPreviewService.validate(req.code(), req.cartTotal(), userId));
  }
}
