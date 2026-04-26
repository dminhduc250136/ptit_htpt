package com.ptit.htpt.orderservice.domain;

import java.math.BigDecimal;

public record OrderItemDto(
    String id,
    String productId,
    String productName,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {}
