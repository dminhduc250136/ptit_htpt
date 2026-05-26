package com.ptit.htpt.notificationservice.messaging.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
import java.util.List;

/**
 * JSON envelope chứa cả 2 loại event: OrderPlaced và OrderStatusChanged (Phase 27 D-11).
 *
 * <p>Đồng bộ CHÍNH XÁC với order-service OrderEventEnvelope (Plan 27-01):
 * payload đổi sang Object + @JsonTypeInfo/@JsonSubTypes để Jackson deserialize đúng loại
 * theo eventType discriminator.
 *
 * <p>notification-service chỉ DESERIALIZE (consumer) — không cần factory createOrder* methods.
 * Accessor helper orderPlacedPayload() / orderStatusChangedPayload() để listener cast an toàn.
 */
public record OrderEventEnvelope(
    String eventId,
    String eventType,
    String occurredAt,
    String traceId,
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "eventType")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = OrderPlacedPayload.class, name = "OrderPlaced"),
        @JsonSubTypes.Type(value = OrderStatusChangedPayload.class, name = "OrderStatusChanged")
    })
    Object payload
) {

  /**
   * Helper: cast payload sang OrderPlacedPayload. Dùng khi eventType="OrderPlaced".
   */
  public OrderPlacedPayload orderPlacedPayload() {
    return (OrderPlacedPayload) payload;
  }

  /**
   * Helper: cast payload sang OrderStatusChangedPayload. Dùng khi eventType="OrderStatusChanged".
   */
  public OrderStatusChangedPayload orderStatusChangedPayload() {
    return (OrderStatusChangedPayload) payload;
  }

  /**
   * Payload cho event OrderPlaced — mang đủ dữ liệu để notification-service render email
   * mà KHÔNG cần gọi REST cross-service (D-11 Phase 27).
   * customerEmail: địa chỉ email khách hàng.
   * Item.productName: tên sản phẩm snapshot tại thời điểm đặt.
   */
  public record OrderPlacedPayload(
      String orderId,
      String userId,
      String customerEmail,
      List<Item> items,
      BigDecimal totalAmount,
      String currency
  ) {}

  public record Item(
      String productId,
      String productName,
      int quantity,
      BigDecimal priceAtPurchase
  ) {}

  /**
   * Payload cho event OrderStatusChanged (Phase 27 — event type mới).
   * customerEmail + customerName: để notification-service gửi email mà KHÔNG gọi REST.
   */
  public record OrderStatusChangedPayload(
      String orderId,
      String userId,
      String customerEmail,
      String newStatus,
      String customerName
  ) {}
}
