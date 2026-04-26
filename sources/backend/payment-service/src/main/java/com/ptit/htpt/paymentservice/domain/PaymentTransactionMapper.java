package com.ptit.htpt.paymentservice.domain;

public final class PaymentTransactionMapper {
  private PaymentTransactionMapper() {}

  public static PaymentTransactionDto toDto(PaymentTransactionEntity e) {
    return new PaymentTransactionDto(
        e.id(), e.sessionId(), e.reference(), e.message(),
        e.amount(), e.method(), e.status(),
        e.createdAt(), e.updatedAt()
    );
  }
}
