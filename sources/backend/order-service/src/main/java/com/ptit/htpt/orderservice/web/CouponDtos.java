package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.domain.CouponType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Phase 20 / Plan 20-02 (D-16): DTO records cho coupon admin CRUD + preview.
 *
 * <p>Validation jakarta annotations reject input invalid trước khi tới service:
 * <ul>
 *   <li>Code: regex {@code ^[A-Z0-9_-]{3,32}$} (3-32 ký tự A-Z, 0-9, _, -)
 *   <li>Type: enum {@link CouponType} (PERCENT|FIXED) — Spring binding reject
 *       string không in enum.
 *   <li>Value: &gt; 0 ({@code @DecimalMin("0.01")})
 *   <li>MinOrderAmount: ≥ 0 ({@code @DecimalMin("0.0")})
 *   <li>MaxTotalUses: nullable hoặc ≥ 1
 * </ul>
 */
public final class CouponDtos {

  private CouponDtos() {}

  /** Admin response DTO + preview embed. {@code type} là String enum.name() để FE consume. */
  public record CouponDto(
      String id,
      String code,
      String type,
      BigDecimal value,
      BigDecimal minOrderAmount,
      Integer maxTotalUses,
      int usedCount,
      Instant expiresAt,
      boolean active,
      Instant createdAt,
      Instant updatedAt
  ) {}

  /** Admin POST /admin/coupons body (D-14, D-16). */
  public record CreateCouponRequest(
      @NotBlank
      @Pattern(regexp = "^[A-Z0-9_-]{3,32}$",
          message = "Mã coupon phải gồm 3-32 ký tự A-Z, 0-9, _, -")
      String code,

      @NotNull
      CouponType type,

      @NotNull
      @DecimalMin(value = "0.01", message = "Giá trị phải lớn hơn 0")
      BigDecimal value,

      @NotNull
      @DecimalMin(value = "0.0", message = "Giá trị tối thiểu không được âm")
      BigDecimal minOrderAmount,

      @Min(value = 1, message = "Số lượt dùng tối đa phải >= 1")
      Integer maxTotalUses,

      Instant expiresAt,

      Boolean active
  ) {}

  /** Admin PUT /admin/coupons/{id} body. Mirror CreateCouponRequest. */
  public record UpdateCouponRequest(
      @NotBlank
      @Pattern(regexp = "^[A-Z0-9_-]{3,32}$",
          message = "Mã coupon phải gồm 3-32 ký tự A-Z, 0-9, _, -")
      String code,

      @NotNull
      CouponType type,

      @NotNull
      @DecimalMin(value = "0.01", message = "Giá trị phải lớn hơn 0")
      BigDecimal value,

      @NotNull
      @DecimalMin(value = "0.0", message = "Giá trị tối thiểu không được âm")
      BigDecimal minOrderAmount,

      @Min(value = 1, message = "Số lượt dùng tối đa phải >= 1")
      Integer maxTotalUses,

      Instant expiresAt,

      Boolean active
  ) {}

  /** Admin PATCH /admin/coupons/{id}/active body. */
  public record ActiveToggleRequest(
      @NotNull Boolean active
  ) {}

  /** Public POST /orders/coupons/validate body (Plan 20-03 wires endpoint). */
  public record CouponPreviewRequest(
      @NotBlank String code,
      @NotNull @DecimalMin(value = "0.0") BigDecimal cartTotal
  ) {}

  /** Preview happy path response (D-08 step 1). */
  public record CouponPreviewResponse(
      String code,
      String type,
      BigDecimal value,
      BigDecimal discountAmount,
      BigDecimal finalTotal,
      String message
  ) {}
}
