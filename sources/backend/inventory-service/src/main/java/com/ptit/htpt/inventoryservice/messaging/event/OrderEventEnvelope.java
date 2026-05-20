package com.ptit.htpt.inventoryservice.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JSON envelope cho event OrderPlaced (D-15) — copy từ order-service Plan 23-03.
 * Format payload thống nhất giữa 3 service:
 *   { eventId, eventType, occurredAt, traceId, payload: { orderId, userId, items[], totalAmount, currency } }
 */
public record OrderEventEnvelope(
    String eventId,
    String eventType,
    String occurredAt,
    String traceId,
    OrderPlacedPayload payload
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

  public record OrderPlacedPayload(
      String orderId,
      String userId,
      List<Item> items,
      BigDecimal totalAmount,
      String currency
  ) {}

  public record Item(
      String productId,
      int quantity,
      BigDecimal priceAtPurchase
  ) {}
}
