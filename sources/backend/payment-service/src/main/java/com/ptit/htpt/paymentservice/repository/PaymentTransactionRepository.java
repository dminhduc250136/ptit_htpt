package com.ptit.htpt.paymentservice.repository;

import com.ptit.htpt.paymentservice.domain.PaymentTransactionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, String> {
  Optional<PaymentTransactionEntity> findBySessionId(String sessionId);

  Optional<PaymentTransactionEntity> findByReference(String reference);
}
