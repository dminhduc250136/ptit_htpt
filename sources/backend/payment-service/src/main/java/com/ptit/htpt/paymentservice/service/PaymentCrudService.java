package com.ptit.htpt.paymentservice.service;

import com.ptit.htpt.paymentservice.domain.PaymentSessionEntity;
import com.ptit.htpt.paymentservice.domain.PaymentTransactionEntity;
import com.ptit.htpt.paymentservice.repository.InMemoryPaymentRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentCrudService {
  private final InMemoryPaymentRepository repository;

  public PaymentCrudService(InMemoryPaymentRepository repository) {
    this.repository = repository;
  }

  public Map<String, Object> listSessions(int page, int size, String sort, boolean includeDeleted) {
    List<PaymentSessionEntity> all = repository.findAllSessions().stream()
        .filter(session -> includeDeleted || !session.deleted())
        .sorted(sessionComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public PaymentSessionEntity getSession(String id, boolean includeDeleted) {
    PaymentSessionEntity session = repository.findSessionById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment session not found"));
    if (!includeDeleted && session.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment session not found");
    }
    return session;
  }

  public PaymentSessionEntity createSession(SessionUpsertRequest request) {
    PaymentSessionEntity session = PaymentSessionEntity.create(
        request.orderId(),
        request.provider(),
        request.amount(),
        request.status()
    );
    return repository.saveSession(session);
  }

  public PaymentSessionEntity updateSession(String id, SessionUpsertRequest request) {
    PaymentSessionEntity current = getSession(id, true);
    PaymentSessionEntity updated = current.update(
        request.orderId(),
        request.provider(),
        request.amount(),
        request.status()
    );
    return repository.saveSession(updated);
  }

  public PaymentSessionEntity updateSessionStatus(String id, SessionStatusRequest request) {
    PaymentSessionEntity current = getSession(id, true);
    return repository.saveSession(current.setStatus(request.status()));
  }

  public void deleteSession(String id) {
    PaymentSessionEntity current = getSession(id, true);
    repository.saveSession(current.softDelete());
  }

  public Map<String, Object> listTransactions(int page, int size, String sort, boolean includeDeleted) {
    List<PaymentTransactionEntity> all = repository.findAllTransactions().stream()
        .filter(transaction -> includeDeleted || !transaction.deleted())
        .sorted(transactionComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public PaymentTransactionEntity getTransaction(String id, boolean includeDeleted) {
    PaymentTransactionEntity transaction = repository.findTransactionById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));
    if (!includeDeleted && transaction.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found");
    }
    return transaction;
  }

  public PaymentTransactionEntity createTransaction(TransactionUpsertRequest request) {
    PaymentTransactionEntity transaction = PaymentTransactionEntity.create(
        request.sessionId(),
        request.reference(),
        request.status(),
        request.message()
    );
    return repository.saveTransaction(transaction);
  }

  public PaymentTransactionEntity updateTransaction(String id, TransactionUpsertRequest request) {
    PaymentTransactionEntity current = getTransaction(id, true);
    PaymentTransactionEntity updated = current.update(
        request.sessionId(),
        request.reference(),
        request.status(),
        request.message()
    );
    return repository.saveTransaction(updated);
  }

  public PaymentTransactionEntity updateTransactionStatus(String id, TransactionStatusRequest request) {
    PaymentTransactionEntity current = getTransaction(id, true);
    return repository.saveTransaction(current.setStatus(request.status()));
  }

  public void deleteTransaction(String id) {
    PaymentTransactionEntity current = getTransaction(id, true);
    repository.saveTransaction(current.softDelete());
  }

  private Comparator<PaymentSessionEntity> sessionComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(PaymentSessionEntity::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<PaymentSessionEntity> comparator = sort.startsWith("status")
        ? Comparator.comparing(PaymentSessionEntity::status, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(PaymentSessionEntity::id);
    return desc ? comparator.reversed() : comparator;
  }

  private Comparator<PaymentTransactionEntity> transactionComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(PaymentTransactionEntity::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<PaymentTransactionEntity> comparator = sort.startsWith("status")
        ? Comparator.comparing(PaymentTransactionEntity::status, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(PaymentTransactionEntity::id);
    return desc ? comparator.reversed() : comparator;
  }

  private <T> Map<String, Object> paginate(List<T> source, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? 20 : Math.min(size, 100);
    int totalElements = source.size();
    int from = Math.min(safePage * safeSize, totalElements);
    int to = Math.min(from + safeSize, totalElements);
    List<T> content = new ArrayList<>(source.subList(from, to));
    int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / safeSize);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("content", content);
    result.put("totalElements", totalElements);
    result.put("totalPages", totalPages);
    result.put("currentPage", safePage);
    result.put("pageSize", safeSize);
    result.put("isFirst", safePage <= 0);
    result.put("isLast", safePage >= Math.max(totalPages - 1, 0));
    return result;
  }

  public record SessionUpsertRequest(
      @NotBlank String orderId,
      @NotBlank String provider,
      @DecimalMin("0.0") BigDecimal amount,
      @NotBlank String status
  ) {}

  public record SessionStatusRequest(@NotBlank String status) {}

  public record TransactionUpsertRequest(
      @NotBlank String sessionId,
      @NotBlank String reference,
      @NotBlank String status,
      String message
  ) {}

  public record TransactionStatusRequest(@NotBlank String status) {}
}
