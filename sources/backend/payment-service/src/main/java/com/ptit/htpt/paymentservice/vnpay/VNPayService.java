package com.ptit.htpt.paymentservice.vnpay;

import com.ptit.htpt.paymentservice.domain.PaymentSessionEntity;
import com.ptit.htpt.paymentservice.domain.PaymentTransactionEntity;
import com.ptit.htpt.paymentservice.messaging.event.PaymentEventEnvelope;
import com.ptit.htpt.paymentservice.messaging.publisher.PaymentEventPublisher;
import com.ptit.htpt.paymentservice.repository.PaymentSessionRepository;
import com.ptit.htpt.paymentservice.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VNPay business logic — xử lý IPN và return callback.
 *
 * IPN là nguồn sự thật duy nhất (D-06):
 *   - processIpn(): verify chữ ký, so khớp amount, idempotent check, update DB, publish event
 *   - buildReturnView(): CHỈ verify chữ ký để FE hiển thị, KHÔNG update DB, KHÔNG publish
 *
 * Threat mitigations:
 *   T-26-01: chữ ký sai → RspCode 97
 *   T-26-02: amount lệch → RspCode 04 + log audit
 *   T-26-03: idempotent terminal → RspCode 02
 *   T-26-04: return URL chỉ hiển thị
 *   T-26-05: KHÔNG log hashSecret
 */
@Service
public class VNPayService {

  private static final Logger log = LoggerFactory.getLogger(VNPayService.class);

  private final VNPaySignature vnPaySignature;
  private final PaymentSessionRepository sessionRepo;
  private final PaymentTransactionRepository transactionRepo;
  private final PaymentEventPublisher eventPublisher;

  public VNPayService(VNPaySignature vnPaySignature,
                      PaymentSessionRepository sessionRepo,
                      PaymentTransactionRepository transactionRepo,
                      PaymentEventPublisher eventPublisher) {
    this.vnPaySignature = vnPaySignature;
    this.sessionRepo = sessionRepo;
    this.transactionRepo = transactionRepo;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Xử lý IPN server-to-server từ VNPay.
   * Trả JSON thuần {RspCode, Message} theo spec VNPay v2.1.0.
   *
   * Flow:
   *   1. Verify chữ ký → 97 nếu sai
   *   2. Load session theo vnp_TxnRef (= paymentSessionId) → 01 nếu không tìm thấy
   *   3. So khớp amount × 100 → 04 nếu lệch
   *   4. Idempotent: transaction đã terminal → 02
   *   5. vnp_ResponseCode=00 → PAID + publish PaymentSucceeded
   *      khác → FAILED + publish PaymentFailed → vẫn trả 00 (đã ghi nhận)
   */
  @Transactional
  public Map<String, String> processIpn(Map<String, String> params) {
    // Bước 1: Verify chữ ký (T-26-01)
    if (!vnPaySignature.verifySignature(params)) {
      log.warn("[IPN-AUDIT] Invalid signature — params.vnp_TxnRef={}", params.get("vnp_TxnRef"));
      return Map.of("RspCode", "97", "Message", "Invalid signature");
    }

    String txnRef = params.get("vnp_TxnRef");
    String vnpTransactionNo = params.getOrDefault("vnp_TransactionNo", "");
    String vnpResponseCode = params.getOrDefault("vnp_ResponseCode", "99");
    String vnpAmountStr = params.getOrDefault("vnp_Amount", "0");

    // Bước 2: Load session theo vnp_TxnRef = paymentSessionId
    Optional<PaymentSessionEntity> sessionOpt = sessionRepo.findById(txnRef);
    if (sessionOpt.isEmpty()) {
      log.warn("[IPN-AUDIT] Session not found — vnp_TxnRef={}", txnRef);
      return Map.of("RspCode", "01", "Message", "Order not found");
    }
    PaymentSessionEntity session = sessionOpt.get();

    // Bước 3: So khớp amount — VNPay gửi amountVnd × 100 (T-26-02)
    long vnpAmount;
    try {
      vnpAmount = Long.parseLong(vnpAmountStr);
    } catch (NumberFormatException e) {
      log.warn("[IPN-AUDIT] Invalid amount format — vnp_Amount={} txnRef={}", vnpAmountStr, txnRef);
      return Map.of("RspCode", "04", "Message", "Invalid amount");
    }
    long expectedAmount = session.amount().multiply(BigDecimal.valueOf(100)).longValue();
    if (vnpAmount != expectedAmount) {
      log.warn("[IPN-AUDIT] Amount mismatch — expected={} got={} txnRef={}", expectedAmount, vnpAmount, txnRef);
      return Map.of("RspCode", "04", "Message", "Invalid amount");
    }

    // Bước 4: Load transaction + idempotent check (T-26-03, Pitfall 3)
    Optional<PaymentTransactionEntity> txnOpt = transactionRepo.findBySessionId(txnRef);
    if (txnOpt.isPresent()) {
      PaymentTransactionEntity existing = txnOpt.get();
      String status = existing.status();
      if ("PAID".equals(status) || "FAILED".equals(status)) {
        log.info("[IPN] Idempotent skip — txnRef={} status={}", txnRef, status);
        return Map.of("RspCode", "02", "Message", "Order already confirmed");
      }
    }

    // Bước 5: Xử lý kết quả
    PaymentTransactionEntity txn;
    if (txnOpt.isPresent()) {
      txn = txnOpt.get();
    } else {
      // Tạo transaction mới nếu chưa có
      txn = PaymentTransactionEntity.create(
          txnRef, vnpTransactionNo, session.amount(), "VNPAY", "PENDING", null);
    }

    String eventType;
    if ("00".equals(vnpResponseCode)) {
      // Thanh toán thành công
      txn.update(txnRef, vnpTransactionNo, session.amount(), "VNPAY", "PAID",
          "VNPay confirmed — code 00");
      transactionRepo.save(txn);
      eventType = "PaymentSucceeded";
      log.info("[IPN] Payment PAID — txnRef={} vnpTransactionNo={}", txnRef, vnpTransactionNo);
    } else {
      // Thanh toán thất bại
      txn.update(txnRef, vnpTransactionNo, session.amount(), "VNPAY", "FAILED",
          "VNPay failed — code " + vnpResponseCode);
      transactionRepo.save(txn);
      eventType = "PaymentFailed";
      log.info("[IPN] Payment FAILED — txnRef={} responseCode={}", txnRef, vnpResponseCode);
    }

    // Publish event afterCommit (D-06)
    PaymentEventEnvelope.PaymentPayload payload = new PaymentEventEnvelope.PaymentPayload(
        session.orderId(),
        txnRef,
        vnpTransactionNo,
        session.amount(),
        "VND"
    );
    eventPublisher.publishPaymentEvent(eventType, payload);

    return Map.of("RspCode", "00", "Message", "Confirm Success");
  }

  /**
   * Xử lý return URL — CHỈ verify chữ ký để FE hiển thị.
   * KHÔNG update DB, KHÔNG publish event (D-06, T-26-04, Pitfall 5).
   *
   * Trả view object cho FE gồm:
   *   - valid: boolean — kết quả verify chữ ký
   *   - orderId: String — resolve từ vnp_TxnRef (paymentSessionId → session.orderId)
   *   - responseCode: String — vnp_ResponseCode
   *   - vnpTransactionNo: String — mã giao dịch VNPay
   *
   * orderId là field bắt buộc — FE Plan 04 dùng làm input cho polling getOrderById.
   */
  public Map<String, Object> buildReturnView(Map<String, String> params) {
    boolean valid = vnPaySignature.verifySignature(params);

    String txnRef = params.get("vnp_TxnRef");
    String responseCode = params.getOrDefault("vnp_ResponseCode", "99");
    String vnpTransactionNo = params.getOrDefault("vnp_TransactionNo", "");

    // Resolve orderId từ session (load read-only — KHÔNG modify)
    String orderId = null;
    if (txnRef != null) {
      Optional<PaymentSessionEntity> sessionOpt = sessionRepo.findById(txnRef);
      if (sessionOpt.isPresent()) {
        orderId = sessionOpt.get().orderId();
      }
    }

    Map<String, Object> view = new LinkedHashMap<>();
    view.put("valid", valid);
    view.put("orderId", orderId);
    view.put("responseCode", responseCode);
    view.put("vnpTransactionNo", vnpTransactionNo);
    return view;
  }
}
