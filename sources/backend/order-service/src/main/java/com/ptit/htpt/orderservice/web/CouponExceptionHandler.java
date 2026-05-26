package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.exception.CouponErrorCode;
import com.ptit.htpt.orderservice.exception.CouponException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Phase 20 / Plan 20-02 (D-11): Translate {@link CouponException} sang
 * {@link com.ptit.htpt.orderservice.api.ApiErrorResponse}-shape body với
 * field bổ sung {@code details} (KHÔNG có trong ApiErrorResponse record).
 *
 * <p>Body shape:
 * <pre>
 * {
 *   "timestamp": "...",
 *   "status": 422,
 *   "error": "Unprocessable Entity",
 *   "code": "COUPON_EXPIRED",
 *   "message": "Mã giảm giá đã hết hạn",
 *   "path": "/orders/coupons/validate",
 *   "traceId": "uuid",
 *   "details": { "minOrderAmount": 100000 }   // optional
 * }
 * </pre>
 */
@RestControllerAdvice
public class CouponExceptionHandler {

  @ExceptionHandler(CouponException.class)
  public ResponseEntity<Map<String, Object>> handle(CouponException ex, HttpServletRequest req) {
    CouponErrorCode ec = ex.errorCode();
    HttpStatus status = HttpStatus.resolve(ec.httpStatus);
    if (status == null) {
      status = HttpStatus.UNPROCESSABLE_ENTITY;
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", ec.httpStatus);
    body.put("error", status.getReasonPhrase());
    body.put("code", ec.name());
    body.put("message", ec.defaultMessageVi);
    body.put("path", req.getRequestURI());
    body.put("traceId", UUID.randomUUID().toString());
    if (ex.details() != null && !ex.details().isEmpty()) {
      body.put("details", ex.details());
    }

    return ResponseEntity.status(status).body(body);
  }
}
