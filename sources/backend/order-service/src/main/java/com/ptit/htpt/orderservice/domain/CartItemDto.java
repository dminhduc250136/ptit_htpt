package com.ptit.htpt.orderservice.domain;

public record CartItemDto(
    String id,
    String productId,
    int quantity
) {}
