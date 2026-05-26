package com.ptit.htpt.orderservice.messaging.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JSON envelope chứa cả 2 loại event: OrderPlaced và OrderStatusChanged (Phase 27 D-11).
 *
 * Quyết định Planner (Pitfall 3 RESEARCH): GIỮ 1 record envelope, đổi field payload sang Object,
 * dùng Jackson @JsonTypeInfo/@JsonSubTypes để deserialize đúng loại theo eventType.
 * Lý do: tách 2 envelope class sẽ buộc refactor OrderEventPublisher generic + listener 2 phía.
 *
 * Format payload thống nhất giữa 3 service:
 *   { eventId, eventType, occurredAt, traceId, payload: { ... } }
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
  public static OrderEventEnvelope createOrderPlaced(String traceId, OrderPlacedPayload payload) {
    return new OrderEventEnvelope(
        UUID.randomUUID().toString(),
        "OrderPlaced",
        Instant.now().toString(),
        traceId,
        payload
    );
  }

  public static OrderEventEnvelope createOrderStatusChanged(String traceId, OrderStatusChangedPayload payload) {
    return new OrderEventEnvelope(
        UUID.randomUUID().toString(),
        "OrderStatusChanged",
        Instant.now().toString(),
        traceId,
        payload
    );
  }

  /**
   * Payload cho event OrderPlaced — mang đủ dữ liệu để notification-service render email
   * mà KHÔNG cần gọi REST cross-service (D-11 Phase 27).
   * customerEmail: địa chỉ email khách hàng (Phase 27 — thêm mới).
   * Item.productName: tên sản phẩm snapshot tại thời điểm đặt (Phase 27 — thêm mới).
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
   * Phát khi admin đổi trạng thái đơn sang shipped/delivered/cancelled.
   * customerEmail + customerName: để notification-service gửi email thông báo mà KHÔNG gọi REST.
   */
  public record OrderStatusChangedPayload(
      String orderId,
      String userId,
      String customerEmail,
      String newStatus,
      String customerName
  ) {}
}
