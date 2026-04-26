package com.ptit.htpt.paymentservice.domain;

public final class PaymentSessionMapper {
  private PaymentSessionMapper() {}

  public static PaymentSessionDto toDto(PaymentSessionEntity e) {
    return new PaymentSessionDto(
        e.id(), e.orderId(), e.provider(), e.amount(), e.status(),
        e.createdAt(), e.updatedAt()
    );
  }
}
