package com.ptit.htpt.orderservice.domain;

import java.util.List;

public record CartDto(
    String id,
    String userId,
    List<CartItemDto> items
) {}
