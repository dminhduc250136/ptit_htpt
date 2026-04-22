package com.ptit.htpt.paymentservice.repository;

import com.ptit.htpt.paymentservice.domain.PaymentSessionEntity;
import com.ptit.htpt.paymentservice.domain.PaymentTransactionEntity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPaymentRepository {
  private final Map<String, PaymentSessionEntity> sessions = new LinkedHashMap<>();
  private final Map<String, PaymentTransactionEntity> transactions = new LinkedHashMap<>();

  public Collection<PaymentSessionEntity> findAllSessions() {
    return sessions.values();
  }

  public Optional<PaymentSessionEntity> findSessionById(String id) {
    return Optional.ofNullable(sessions.get(id));
  }

  public PaymentSessionEntity saveSession(PaymentSessionEntity session) {
    sessions.put(session.id(), session);
    return session;
  }

  public Collection<PaymentTransactionEntity> findAllTransactions() {
    return transactions.values();
  }

  public Optional<PaymentTransactionEntity> findTransactionById(String id) {
    return Optional.ofNullable(transactions.get(id));
  }

  public PaymentTransactionEntity saveTransaction(PaymentTransactionEntity transaction) {
    transactions.put(transaction.id(), transaction);
    return transaction;
  }
}
