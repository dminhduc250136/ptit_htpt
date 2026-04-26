package com.ptit.htpt.paymentservice.repository;

import com.ptit.htpt.paymentservice.domain.PaymentSessionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentSessionRepository extends JpaRepository<PaymentSessionEntity, String> {
  Optional<PaymentSessionEntity> findByOrderId(String orderId);
}
