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
    Instant createdAt,
    Instant updatedAt
) {
  /** Alias cho FE legacy contract (`totalAmount`). */
  @JsonProperty("totalAmount")
  public BigDecimal totalAmount() {
    return total;
  }
}
