package com.ptit.htpt.orderservice.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class OrderMapper {
  private OrderMapper() {}

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static OrderDto toDto(OrderEntity e) {
    List<OrderItemDto> items = e.items() == null ? Collections.emptyList() :
        e.items().stream()
            .map(i -> new OrderItemDto(
                i.id(), i.productId(), i.productName(),
                i.quantity(), i.unitPrice(), i.lineTotal()))
            .toList();

    Map<String, Object> shippingAddress = null;
    if (e.shippingAddress() != null) {
      try {
        shippingAddress = MAPPER.readValue(e.shippingAddress(),
            new TypeReference<Map<String, Object>>() {});
      } catch (Exception ex) {
        // malformed JSON — trả empty map thay vì crash
        shippingAddress = Collections.emptyMap();
      }
    }

    return new OrderDto(
        e.id(), e.userId(), e.total(), e.status(), e.note(),
        items, shippingAddress, e.paymentMethod(),
        e.discountAmount(), e.couponCode(),
        e.createdAt(), e.updatedAt()
    );
  }
}
