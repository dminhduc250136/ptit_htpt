package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.api.ApiResponse;
import com.ptit.htpt.orderservice.service.CouponService;
import com.ptit.htpt.orderservice.web.CouponDtos.ActiveToggleRequest;
import com.ptit.htpt.orderservice.web.CouponDtos.CouponDto;
import com.ptit.htpt.orderservice.web.CouponDtos.CreateCouponRequest;
import com.ptit.htpt.orderservice.web.CouponDtos.UpdateCouponRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 20 / Plan 20-02 (D-14): Admin coupon CRUD controller.
 *
 * <p>5 endpoints theo D-14 verbatim — gateway sẽ route
 * {@code /api/orders/admin/coupons/**} → {@code /admin/coupons/**} (Plan 20-04).
 *
 * <p>Auth: mỗi handler gate qua {@link JwtRoleGuard#requireAdmin(String)}
 * (precedent {@link AdminStatsController} Phase 9 D-05).
 */
@RestController
@RequestMapping("/admin/coupons")
public class AdminCouponController {

  private final CouponService couponService;
  private final JwtRoleGuard jwtRoleGuard;

  public AdminCouponController(CouponService couponService, JwtRoleGuard jwtRoleGuard) {
    this.couponService = couponService;
    this.jwtRoleGuard = jwtRoleGuard;
  }

  @GetMapping
  public ApiResponse<Page<CouponDto>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean active,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "Coupons listed",
        couponService.list(page, size, sort, q, active));
  }

  @GetMapping("/{id}")
  public ApiResponse<CouponDto> get(
      @PathVariable String id,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "Coupon loaded", couponService.getById(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<CouponDto> create(
      @Valid @RequestBody CreateCouponRequest req,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(201, "Coupon created", couponService.create(req));
  }

  @PutMapping("/{id}")
  public ApiResponse<CouponDto> update(
      @PathVariable String id,
      @Valid @RequestBody UpdateCouponRequest req,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "Coupon updated", couponService.update(id, req));
  }

  @PatchMapping("/{id}/active")
  public ApiResponse<CouponDto> toggleActive(
      @PathVariable String id,
      @Valid @RequestBody ActiveToggleRequest req,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "Coupon active toggled",
        couponService.setActive(id, req.active()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable String id,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    jwtRoleGuard.requireAdmin(authHeader);
    couponService.delete(id);
  }
}
