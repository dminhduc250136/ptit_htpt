package com.ptit.htpt.paymentservice.momo;

import com.ptit.htpt.paymentservice.domain.PaymentSessionEntity;
import com.ptit.htpt.paymentservice.domain.PaymentTransactionEntity;
import com.ptit.htpt.paymentservice.messaging.event.PaymentEventEnvelope;
import com.ptit.htpt.paymentservice.messaging.publisher.PaymentEventPublisher;
import com.ptit.htpt.paymentservice.repository.PaymentSessionRepository;
import com.ptit.htpt.paymentservice.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * MoMo business logic — xây dựng payment URL và xử lý IPN callback.
 *
 * IPN là nguồn sự thật duy nhất (D-06 Phase 26.1):
 *   - buildPaymentUrl(): POST sang MoMo create endpoint, trả payUrl
 *   - processIpn(): verify chữ ký, so khớp amount, idempotent check, update DB, publish event
 *   - buildReturnView(): CHỈ verify để FE hiển thị, KHÔNG update DB (T-26.1-04)
 *
 * Threat mitigations:
 *   T-26.1-01: chữ ký sai → log audit, KHÔNG update DB
 *   T-26.1-02: amount lệch → log audit, KHÔNG PAID
 *   T-26.1-03: idempotent terminal → skip, KHÔNG publish lại
 *   T-26.1-04: return URL chỉ hiển thị
 *   T-26.1-05: KHÔNG log secretKey
 */
@Service
public class MomoService {

  private static final Logger log = LoggerFactory.getLogger(MomoService.class);
  private static final String REQUEST_TYPE = "payWithMethod";
  private static final String LANG = "vi";

  private final MomoSignature momoSignature;
  private final MomoConfig config;
  private final PaymentSessionRepository sessionRepo;
  private final PaymentTransactionRepository transactionRepo;
  private final PaymentEventPublisher eventPublisher;
  private final RestTemplate restTemplate;

  public MomoService(MomoSignature momoSignature,
                     MomoConfig config,
                     PaymentSessionRepository sessionRepo,
                     PaymentTransactionRepository transactionRepo,
                     PaymentEventPublisher eventPublisher,
                     RestTemplate restTemplate) {
    this.momoSignature = momoSignature;
    this.config = config;
    this.sessionRepo = sessionRepo;
    this.transactionRepo = transactionRepo;
    this.eventPublisher = eventPublisher;
    this.restTemplate = restTemplate;
  }

  /**
   * Build MoMo payment URL bằng POST sang MoMo create endpoint.
   *
   * @param paymentSessionId  payment session UUID (= requestId = orderId cho MoMo)
   * @param internalOrderId   order ID nội bộ để link-back khi IPN về (D-11)
   * @param amountVnd         số tiền VND raw (KHÔNG ×100)
   * @param orderInfo         mô tả đơn hàng
   * @return payUrl từ MoMo response
   * @throws ResponseStatusException 502 nếu MoMo create thất bại hoặc không gọi được
   */
  public String buildPaymentUrl(String paymentSessionId, String internalOrderId,
                                long amountVnd, String orderInfo) {
    // D-11: extraData = Base64(JSON) chứa orderId nội bộ để link-back khi IPN về
    String extraData = Base64.getEncoder().encodeToString(
        ("{\"orderId\":\"" + internalOrderId + "\"}").getBytes(StandardCharsets.UTF_8));

    // Build sign data (thứ tự cố định theo MoMo doc D-10)
    String signData = momoSignature.buildSignDataCreate(
        paymentSessionId, amountVnd, extraData,
        paymentSessionId,  // orderId = requestId = paymentSessionId (D-11)
        orderInfo, REQUEST_TYPE);
    String signature = momoSignature.sign(signData);

    // Build request body
    Map<String, Object> body = new HashMap<>();
    body.put("partnerCode", config.partnerCode());
    body.put("partnerName", "TMDT Demo");
    body.put("storeId", "TmdtStore");
    body.put("requestType", REQUEST_TYPE);
    body.put("ipnUrl", config.ipnUrl());
    body.put("redirectUrl", config.returnUrl());
    body.put("orderId", paymentSessionId);
    body.put("amount", amountVnd);  // raw VND (KHÔNG ×100)
    body.put("lang", LANG);
    body.put("orderInfo", orderInfo);
    body.put("requestId", paymentSessionId);
    body.put("extraData", extraData);
    body.put("signature", signature);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    try {
      @SuppressWarnings("unchecked")
      var response = restTemplate.postForEntity(
          config.payUrl(), new HttpEntity<>(body, headers), Map.class);
      if (response.getBody() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MoMo create session trả body rỗng");
      }
      Object resultCode = response.getBody().get("resultCode");
      if (Integer.valueOf(0).equals(resultCode) || "0".equals(String.valueOf(resultCode))) {
        return String.valueOf(response.getBody().get("payUrl"));
      }
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "MoMo create session lỗi: " + response.getBody().get("message"));
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("[MOMO] Không gọi được MoMo create endpoint: {}", ex.getMessage());
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không gọi được MoMo create endpoint");
    }
  }

  /**
   * Xử lý IPN POST JSON từ MoMo.
   * Controller luôn trả HTTP 204 (D-12) — method này chỉ process logic, không throw.
   *
   * Flow:
   *   1. Verify chữ ký (T-26.1-01)
   *   2. Load session theo requestId (= paymentSessionId)
   *   3. So khớp amount VND raw (T-26.1-02)
   *   4. Idempotent: transaction đã terminal → skip (T-26.1-03)
   *   5. resultCode=0 → PAID + publish PaymentSucceeded
   *      khác → FAILED + publish PaymentFailed
   */
  @Transactional
  public void processIpn(Map<String, Object> ipnPayload) {
    // FIX 26.1: MoMo IPN payload KHÔNG kèm accessKey (đó là secret merchant-side, KHÔNG
    // gửi qua mạng public). Inject từ config để verifyIpn tính HMAC SHA256 đúng — cùng
    // pattern đã áp dụng cho return URL ở buildReturnView (line 239).
    ipnPayload.putIfAbsent("accessKey", config.accessKey());

    // Bước 1: Verify chữ ký (T-26.1-01)
    if (!momoSignature.verifyIpn(ipnPayload)) {
      log.warn("[MOMO-IPN] Invalid signature — requestId={}", ipnPayload.get("requestId"));
      return;
    }

    String requestId = String.valueOf(ipnPayload.getOrDefault("requestId", ""));

    // Bước 2: Load session
    Optional<PaymentSessionEntity> sessionOpt = sessionRepo.findById(requestId);
    if (sessionOpt.isEmpty()) {
      log.warn("[MOMO-IPN] Session not found — requestId={}", requestId);
      return;
    }
    PaymentSessionEntity session = sessionOpt.get();

    // Bước 3: So khớp amount (T-26.1-02) — MoMo gửi raw VND, KHÔNG ×100
    long ipnAmount;
    try {
      ipnAmount = Long.parseLong(String.valueOf(ipnPayload.get("amount")));
    } catch (NumberFormatException e) {
      log.warn("[MOMO-IPN] Invalid amount format — amount={} requestId={}", ipnPayload.get("amount"), requestId);
      return;
    }
    long expectedAmount = session.amount().longValue();
    if (ipnAmount != expectedAmount) {
      log.warn("[MOMO-IPN] Amount mismatch — expected={} got={} requestId={}", expectedAmount, ipnAmount, requestId);
      return;
    }

    // Bước 4: Idempotent check (T-26.1-03)
    Optional<PaymentTransactionEntity> txnOpt = transactionRepo.findBySessionId(requestId);
    if (txnOpt.isPresent()) {
      String status = txnOpt.get().status();
      if ("PAID".equals(status) || "FAILED".equals(status)) {
        log.info("[MOMO-IPN] Skipped — terminal status={} requestId={}", status, requestId);
        return;
      }
    }

    // Bước 5: Xử lý kết quả
    String transId = String.valueOf(ipnPayload.getOrDefault("transId", ""));
    int resultCode;
    try {
      resultCode = Integer.parseInt(String.valueOf(ipnPayload.getOrDefault("resultCode", "99")));
    } catch (NumberFormatException e) {
      resultCode = 99;
    }

    PaymentTransactionEntity txn;
    if (txnOpt.isPresent()) {
      txn = txnOpt.get();
    } else {
      txn = PaymentTransactionEntity.create(requestId, transId, session.amount(), "MOMO", "PENDING", null);
    }

    String eventType;
    if (resultCode == 0) {
      txn.update(requestId, transId, session.amount(), "MOMO", "PAID", "MoMo confirmed — resultCode 0");
      transactionRepo.save(txn);
      eventType = "PaymentSucceeded";
      log.info("[MOMO-IPN] Payment PAID — requestId={} transId={}", requestId, transId);
    } else {
      txn.update(requestId, transId, session.amount(), "MOMO", "FAILED", "MoMo failed — resultCode " + resultCode);
      transactionRepo.save(txn);
      eventType = "PaymentFailed";
      log.info("[MOMO-IPN] Payment FAILED — requestId={} resultCode={}", requestId, resultCode);
    }

    // Publish event afterCommit (D-06)
    PaymentEventEnvelope.PaymentPayload payload = new PaymentEventEnvelope.PaymentPayload(
        session.orderId(),
        requestId,
        transId,
        session.amount(),
        "VND"
    );
    eventPublisher.publishPaymentEvent(eventType, payload);
  }

  /**
   * Xử lý return URL — CHỈ verify chữ ký để FE hiển thị.
   * KHÔNG update DB, KHÔNG publish event (T-26.1-04).
   *
   * @param params query params từ MoMo redirect (GET request)
   * @return Map với {valid, orderId, resultCode, paymentTransactionNo}
   */
  public Map<String, Object> buildReturnView(Map<String, String> params) {
    // Convert Map<String,String> to Map<String,Object> cho verifyIpn
    Map<String, Object> paramsAsObject = new HashMap<>(params);
    // FIX 26.1: MoMo return URL KHÔNG kèm accessKey trong query string (đó là secret merchant-side).
    // Inject từ config để verifyIpn tính HMAC SHA256 đúng (dùng chung sign data format với IPN).
    paramsAsObject.putIfAbsent("accessKey", config.accessKey());
    boolean valid = momoSignature.verifyIpn(paramsAsObject);

    String requestId = params.get("requestId");
    String resultCode = params.getOrDefault("resultCode", "99");
    String transId = params.getOrDefault("transId", "");

    // Resolve orderId từ session (read-only — KHÔNG modify)
    String orderId = null;
    if (requestId != null) {
      Optional<PaymentSessionEntity> sessionOpt = sessionRepo.findById(requestId);
      if (sessionOpt.isPresent()) {
        orderId = sessionOpt.get().orderId();
      } else {
        valid = false;
      }
    }

    Map<String, Object> view = new LinkedHashMap<>();
    view.put("valid", valid);
    view.put("orderId", orderId);
    view.put("resultCode", resultCode);
    view.put("paymentTransactionNo", transId);
    return view;
  }
}
