package com.ptit.htpt.orderservice.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Wire format cho controller response. KHÔNG có field `deleted` để tránh leak qua Jackson.
 *
 * <p>Field `total` được serialize thành cả `total` và `totalAmount` (FE legacy contract Phase 1
 * đã consume `totalAmount`).
 *
 * <p>Phase 8 Plan 02: thêm items (per-item breakdown), shippingAddress (JSONB object), paymentMethod.
 *
 * <p>Phase 20 Plan 03 (D-23, D-24): thêm 2 field snapshot {@code discountAmount} +
 * {@code couponCode} cho FE display ở /profile/orders/[id] và /admin/orders/[id].
 * {@code discountAmount} mặc định BigDecimal.ZERO (DB column NOT NULL DEFAULT 0).
 * {@code couponCode} nullable cho order chưa áp coupon (backward compat).
 *
 * <p>Phase 26 Plan 02 (PAY-04, D-02): thêm 3 field payment.
 * {@code paymentStatus} — PENDING/PAID/FAILED, map từ entity.
 * {@code paymentTransactionNo} — nullable, map từ entity. Phase 26.1 (D-07): renamed từ
 * {@code vnpTransactionNo} để dùng chung cho mọi gateway.
 * {@code paymentUrl} — nullable, transient (KHÔNG map từ entity); set thủ công trong
 * OrderCrudService nhánh MOMO (Plan 03).
 */
public record OrderDto(
    String id,
    String userId,
    BigDecimal total,
    String status,
    String note,
    List<OrderItemDto> items,
    Map<String, Object> shippingAddress,
    String paymentMethod,
    BigDecimal discountAmount,
    String couponCode,
    String paymentStatus,
    String paymentTransactionNo,
    String paymentUrl,
    Instant createdAt,
    Instant updatedAt
) {
  /** Alias cho FE legacy contract (`totalAmount`). */
  @JsonProperty("totalAmount")
  public BigDecimal totalAmount() {
    return total;
  }
}
