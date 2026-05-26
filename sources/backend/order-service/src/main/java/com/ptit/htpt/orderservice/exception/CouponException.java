package com.ptit.htpt.orderservice.exception;

import java.util.Map;

/**
 * Phase 20 / Plan 20-02 (D-11): Domain exception cho coupon system.
 *
 * <p>{@link CouponExceptionHandler} translate sang
 * {@link com.ptit.htpt.orderservice.api.ApiErrorResponse} với HTTP status từ
 * {@link CouponErrorCode#httpStatus}.
 *
 * <p>{@code details} optional map — ví dụ {@code MIN_ORDER_NOT_MET} kèm
 * {@code minOrderAmount} để FE hiển thị "cần thêm X VND".
 */
public class CouponException extends RuntimeException {

  private final CouponErrorCode errorCode;
  private final Map<String, Object> details;

  public CouponException(CouponErrorCode errorCode) {
    this(errorCode, Map.of());
  }

  public CouponException(CouponErrorCode errorCode, Map<String, Object> details) {
    super(errorCode.defaultMessageVi);
    this.errorCode = errorCode;
    this.details = details == null ? Map.of() : Map.copyOf(details);
  }

  public CouponErrorCode errorCode() {
    return errorCode;
  }

  public Map<String, Object> details() {
    return details;
  }
}
