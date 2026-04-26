package com.ptit.htpt.orderservice.domain;

public final class OrderMapper {
  private OrderMapper() {}

  public static OrderDto toDto(OrderEntity e) {
    return new OrderDto(
        e.id(), e.userId(), e.total(), e.status(),
        e.note(), e.createdAt(), e.updatedAt()
    );
  }
}
