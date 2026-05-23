package com.ptit.htpt.paymentservice.service;

import com.ptit.htpt.paymentservice.domain.PaymentSessionEntity;
import com.ptit.htpt.paymentservice.domain.PaymentTransactionEntity;
import com.ptit.htpt.paymentservice.momo.MomoService;
import com.ptit.htpt.paymentservice.repository.PaymentSessionRepository;
import com.ptit.htpt.paymentservice.repository.PaymentTransactionRepository;
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
  private final PaymentSessionRepository sessionRepo;
  private final PaymentTransactionRepository transactionRepo;
  private final MomoService momoService;

  public PaymentCrudService(PaymentSessionRepository sessionRepo,
                             PaymentTransactionRepository transactionRepo,
                             MomoService momoService) {
    this.sessionRepo = sessionRepo;
    this.transactionRepo = transactionRepo;
    this.momoService = momoService;
  }

  public Map<String, Object> listSessions(int page, int size, String sort, boolean includeDeleted) {
    // Sau Phase 5: @SQLRestriction("deleted=false") tự động filter soft-deleted records
    // → includeDeleted flag giữ trong API contract nhưng không còn tác dụng (deleted records không hiển thị).
    List<PaymentSessionEntity> all = sessionRepo.findAll().stream()
        .sorted(sessionComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public PaymentSessionEntity getSession(String id, boolean includeDeleted) {
    return sessionRepo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment session not found"));
  }

  /**
   * Tạo payment session. Nếu provider=MOMO, build paymentUrl bằng POST sang MoMo create endpoint.
   * paymentSessionId = sessionId (UUID mới mỗi session — idempotency key D-11).
   * orderInfo chứa mã đơn để hiển thị trên cổng MoMo.
   */
  public SessionCreateResult createSession(SessionUpsertRequest request) {
    // Status mặc định PENDING nếu không truyền
    String status = request.status() != null && !request.status().isBlank() ? request.status() : "PENDING";
    PaymentSessionEntity session = PaymentSessionEntity.create(
        request.orderId(),
        request.provider(),
        request.amount(),
        status
    );
    PaymentSessionEntity saved = sessionRepo.save(session);

    String paymentUrl = null;
    if ("MOMO".equalsIgnoreCase(request.provider())) {
      long amountVnd = saved.amount().longValue();  // raw VND, KHÔNG ×100
      String orderInfo = "Thanh toan don " + saved.orderId();
      paymentUrl = momoService.buildPaymentUrl(
          saved.id(),
          request.orderId(),
          amountVnd,
          orderInfo
      );
    }
    return new SessionCreateResult(saved, paymentUrl);
  }

  /** DTO kết quả tạo session — paymentUrl chỉ non-null khi provider=MOMO. */
  public record SessionCreateResult(PaymentSessionEntity session, String paymentUrl) {}

  public PaymentSessionEntity updateSession(String id, SessionUpsertRequest request) {
    PaymentSessionEntity current = getSession(id, true);
    String status = request.status() != null && !request.status().isBlank() ? request.status() : current.status();
    current.update(request.orderId(), request.provider(), request.amount(), status);
    return sessionRepo.save(current);
  }

  public PaymentSessionEntity updateSessionStatus(String id, SessionStatusRequest request) {
    PaymentSessionEntity current = getSession(id, true);
    current.setStatus(request.status());
    return sessionRepo.save(current);
  }

  public void deleteSession(String id) {
    PaymentSessionEntity current = getSession(id, true);
    // @SQLDelete maps repository.delete(...) to UPDATE SET deleted=true
    sessionRepo.delete(current);
  }

  public Map<String, Object> listTransactions(int page, int size, String sort, boolean includeDeleted) {
    List<PaymentTransactionEntity> all = transactionRepo.findAll().stream()
        .sorted(transactionComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public PaymentTransactionEntity getTransaction(String id, boolean includeDeleted) {
    return transactionRepo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));
  }

  public PaymentTransactionEntity createTransaction(TransactionUpsertRequest request) {
    PaymentTransactionEntity transaction = PaymentTransactionEntity.create(
        request.sessionId(),
        request.reference(),
        request.amount(),
        request.method(),
        request.status(),
        request.message()
    );
    return transactionRepo.save(transaction);
  }

  public PaymentTransactionEntity updateTransaction(String id, TransactionUpsertRequest request) {
    PaymentTransactionEntity current = getTransaction(id, true);
    current.update(request.sessionId(), request.reference(), request.amount(),
        request.method(), request.status(), request.message());
    return transactionRepo.save(current);
  }

  public PaymentTransactionEntity updateTransactionStatus(String id, TransactionStatusRequest request) {
    PaymentTransactionEntity current = getTransaction(id, true);
    current.setStatus(request.status());
    return transactionRepo.save(current);
  }

  public void deleteTransaction(String id) {
    PaymentTransactionEntity current = getTransaction(id, true);
    transactionRepo.delete(current);
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
      String status,  // nullable — default PENDING
      String clientIp // nullable — giữ lại cho backward compat, MoMo không dùng
  ) {}

  public record SessionStatusRequest(@NotBlank String status) {}

  public record TransactionUpsertRequest(
      @NotBlank String sessionId,
      @NotBlank String reference,
      BigDecimal amount,
      String method,
      @NotBlank String status,
      String message
  ) {}

  public record TransactionStatusRequest(@NotBlank String status) {}
}
