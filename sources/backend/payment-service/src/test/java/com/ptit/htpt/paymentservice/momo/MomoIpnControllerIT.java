package com.ptit.htpt.paymentservice.momo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ptit.htpt.paymentservice.domain.PaymentSessionEntity;
import com.ptit.htpt.paymentservice.domain.PaymentTransactionEntity;
import com.ptit.htpt.paymentservice.messaging.publisher.PaymentEventPublisher;
import com.ptit.htpt.paymentservice.repository.PaymentSessionRepository;
import com.ptit.htpt.paymentservice.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Unit test (Mockito) cho MomoService IPN logic + MomoController.
 * Tên giữ "*IT" để bám sát plan — test mô phỏng integration behavior.
 *
 * Không dùng Spring context — tránh dependency conflict với @EnableConfigurationProperties.
 * MomoConfig tạo thủ công với sandbox public credentials (D-18).
 * Pattern analog VNPayIpnControllerIT (Phase 26 §Deviation 1).
 */
@ExtendWith(MockitoExtension.class)
class MomoIpnControllerIT {

  // Sandbox public credentials (D-18)
  private static final MomoConfig CFG = new MomoConfig(
      "MOMO",
      "F8BBA842ECF85",
      "K951B6PE1waDMi640xX08PD3vg6EkVlz",
      "https://test-payment.momo.vn/v2/gateway/api/create",
      "http://localhost:3000/checkout/result",
      "https://example.test/api/payments/momo/ipn"
  );
  private static final String SESSION_ID = "session-test-001";
  private static final String ORDER_ID = "order-test-001";
  private static final long AMOUNT_VND = 100_000L;

  @Mock
  private PaymentSessionRepository sessionRepo;
  @Mock
  private PaymentTransactionRepository txRepo;
  @Mock
  private PaymentEventPublisher publisher;
  @Mock
  private RestTemplate restTemplate;

  private MomoSignature sig;
  private MomoService service;
  private MomoController controller;

  @BeforeEach
  void setUp() {
    sig = new MomoSignature(CFG);
    service = new MomoService(sig, CFG, sessionRepo, txRepo, publisher, restTemplate);
    controller = new MomoController(service);
  }

  /**
   * Build IPN payload hợp lệ với signature thật tính từ CFG.secretKey.
   */
  private Map<String, Object> buildValidIpnPayload(String resultCode, long amount, String requestId) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("accessKey", CFG.accessKey());
    payload.put("amount", String.valueOf(amount));
    payload.put("extraData", "eyJvcmRlcklkIjoib3JkLTAwMSJ9");
    payload.put("message", "Successful.");
    payload.put("orderId", requestId);
    payload.put("orderInfo", "Thanh toan don");
    payload.put("orderType", "momo_wallet");
    payload.put("partnerCode", CFG.partnerCode());
    payload.put("payType", "qr");
    payload.put("requestId", requestId);
    payload.put("responseTime", "1716441600000");
    payload.put("resultCode", resultCode);
    payload.put("transId", "3498980476");
    // Tính signature thật
    String signData = sig.buildSignDataIpn(payload);
    payload.put("signature", sig.sign(signData));
    return payload;
  }

  private PaymentSessionEntity mockSession() {
    return PaymentSessionEntity.create(ORDER_ID, "MOMO", BigDecimal.valueOf(AMOUNT_VND), "PENDING");
  }

  @Test
  @DisplayName("validIpnSucceeded: resultCode=0 + amount khớp → HTTP 204, transaction PAID, publish PaymentSucceeded")
  void validIpnSucceededUpdatesAndPublishes() {
    Map<String, Object> payload = buildValidIpnPayload("0", AMOUNT_VND, SESSION_ID);
    when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(mockSession()));
    when(txRepo.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
    when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ResponseEntity<Void> response = controller.ipn(payload);

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    verify(publisher, times(1)).publishPaymentEvent(eq("PaymentSucceeded"), any());
  }

  @Test
  @DisplayName("duplicateIpn: transaction đã PAID → HTTP 204, KHÔNG update lại, KHÔNG publish lại")
  void duplicateIpnSkipsOnTerminalStatus() {
    Map<String, Object> payload = buildValidIpnPayload("0", AMOUNT_VND, SESSION_ID);
    when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(mockSession()));

    PaymentTransactionEntity existing = PaymentTransactionEntity.create(
        SESSION_ID, "3498980476", BigDecimal.valueOf(AMOUNT_VND), "MOMO", "PAID", "done");
    when(txRepo.findBySessionId(SESSION_ID)).thenReturn(Optional.of(existing));

    ResponseEntity<Void> response = controller.ipn(payload);

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    verify(txRepo, never()).save(any());
    verify(publisher, never()).publishPaymentEvent(anyString(), any());
  }

  @Test
  @DisplayName("invalidSignature: chữ ký sai → HTTP 204 (MoMo spec), KHÔNG update transaction")
  void invalidSignatureDoesNotUpdate() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("accessKey", CFG.accessKey());
    payload.put("amount", String.valueOf(AMOUNT_VND));
    payload.put("extraData", "");
    payload.put("message", "Successful.");
    payload.put("orderId", SESSION_ID);
    payload.put("orderInfo", "test");
    payload.put("orderType", "momo_wallet");
    payload.put("partnerCode", CFG.partnerCode());
    payload.put("payType", "qr");
    payload.put("requestId", SESSION_ID);
    payload.put("responseTime", "1716441600000");
    payload.put("resultCode", "0");
    payload.put("transId", "9999999");
    payload.put("signature", "invalidsignaturexxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

    ResponseEntity<Void> response = controller.ipn(payload);

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    verify(txRepo, never()).save(any());
    verify(publisher, never()).publishPaymentEvent(anyString(), any());
  }

  @Test
  @DisplayName("amountMismatch: IPN amount != session.amount → HTTP 204, KHÔNG PAID")
  void amountMismatchDoesNotUpdate() {
    // Session amount = 100k VND, IPN gửi 200k VND (lệch)
    Map<String, Object> payload = buildValidIpnPayload("0", 200_000L, SESSION_ID);
    when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(mockSession())); // session = 100k

    ResponseEntity<Void> response = controller.ipn(payload);

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    verify(txRepo, never()).save(any());
    verify(publisher, never()).publishPaymentEvent(anyString(), any());
  }

  @Test
  @DisplayName("resultCodeNonZero: resultCode != 0 → transaction FAILED, publish PaymentFailed, trả 204")
  void resultCodeNonZeroSetsFailedAndPublishes() {
    Map<String, Object> payload = buildValidIpnPayload("99", AMOUNT_VND, SESSION_ID);
    when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(mockSession()));
    when(txRepo.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
    when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ResponseEntity<Void> response = controller.ipn(payload);

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    verify(publisher, times(1)).publishPaymentEvent(eq("PaymentFailed"), any());
  }
}
