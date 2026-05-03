package com.ptit.htpt.orderservice.domain;

import java.util.List;

public final class CartMapper {
  private CartMapper() {}

  public static CartDto toDto(CartEntity cart) {
    List<CartItemDto> items = cart.items().stream()
        .map(i -> new CartItemDto(i.id(), i.productId(), i.quantity()))
        .toList();
    return new CartDto(cart.id(), cart.userId(), items);
  }
}
