package com.ptit.htpt.paymentservice.vnpay;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test (Mockito) cho VNPayService IPN logic.
 * Tên giữ "*IT" để bám sát plan — test mô phỏng integration behavior.
 *
 * Không dùng Spring context — tránh dependency conflict với @EnableConfigurationProperties.
 * VNPayConfig tạo thủ công với secret cố định.
 */
@ExtendWith(MockitoExtension.class)
class VNPayIpnControllerIT {

  private static final String TEST_SECRET = "TESTSECRETKEY2026";
  private static final String SESSION_ID = "session-test-001";
  private static final String ORDER_ID = "order-test-001";
  private static final long AMOUNT_VND = 100_000L;

  @Mock
  private PaymentSessionRepository sessionRepo;
  @Mock
  private PaymentTransactionRepository transactionRepo;
  @Mock
  private PaymentEventPublisher eventPublisher;

  private VNPayService vnPayService;

  @BeforeEach
  void setUp() {
    VNPayConfig cfg = new VNPayConfig("TESTCODE", TEST_SECRET,
        "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
        "http://localhost:3000/checkout/result", "");
    VNPaySignature sig = new VNPaySignature(cfg);
    vnPayService = new VNPayService(sig, sessionRepo, transactionRepo, eventPublisher);
  }

  /**
   * Build IPN params map với vnp_SecureHash hợp lệ tính từ TEST_SECRET.
   */
  private Map<String, String> buildIpnParams(String responseCode, long amountVnd) {
    Map<String, String> params = new TreeMap<>();
    params.put("vnp_TmnCode", "TESTCODE");
    params.put("vnp_Amount", String.valueOf(amountVnd * 100));
    params.put("vnp_TxnRef", SESSION_ID);
    params.put("vnp_OrderInfo", "Don hang test");
    params.put("vnp_ResponseCode", responseCode);
    params.put("vnp_TransactionNo", "VNP-TXN-001");

    StringBuilder hashData = new StringBuilder();
    for (Iterator<Map.Entry<String, String>> it = params.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, String> e = it.next();
      hashData.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII))
          .append('=')
          .append(URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII));
      if (it.hasNext()) hashData.append('&');
    }
    params.put("vnp_SecureHash", VNPaySignature.hmacSHA512(TEST_SECRET, hashData.toString()));
    return params;
  }

  private PaymentSessionEntity mockSession() {
    return PaymentSessionEntity.create(ORDER_ID, "VNPAY", BigDecimal.valueOf(AMOUNT_VND), "PENDING");
  }

  @Test
  @DisplayName("IPN valid + responseCode=00 → RspCode 00, publishPaymentEvent gọi 1 lần")
  void ipn_validSignature_responseCode00_returnSuccess() {
    Map<String, String> params = buildIpnParams("00", AMOUNT_VND);
    when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(mockSession()));
    when(transactionRepo.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
    when(transactionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, String> result = vnPayService.processIpn(params);

    assertThat(result.get("RspCode")).isEqualTo("00");
    verify(eventPublisher, times(1)).publishPaymentEvent(eq("PaymentSucceeded"), any());
  }

  @Test
  @DisplayName("IPN gọi lại lần 2 (transaction đã PAID) → RspCode 02, không publish lại")
  void ipn_idempotent_alreadyPaid_returns02() {
    Map<String, String> params = buildIpnParams("00", AMOUNT_VND);
    when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(mockSession()));

    PaymentTransactionEntity existing = PaymentTransactionEntity.create(
        SESSION_ID, "VNP-TXN-001", BigDecimal.valueOf(AMOUNT_VND), "VNPAY", "PAID", "done");
    when(transactionRepo.findBySessionId(SESSION_ID)).thenReturn(Optional.of(existing));

    Map<String, String> result = vnPayService.processIpn(params);

    assertThat(result.get("RspCode")).isEqualTo("02");
    verify(eventPublisher, never()).publishPaymentEvent(anyString(), any());
  }

  @Test
  @DisplayName("IPN với chữ ký sai → RspCode 97, không update transaction")
  void ipn_invalidSignature_returns97() {
    Map<String, String> params = new TreeMap<>();
    params.put("vnp_TmnCode", "TESTCODE");
    params.put("vnp_Amount", "10000000");
    params.put("vnp_TxnRef", SESSION_ID);
    params.put("vnp_ResponseCode", "00");
    params.put("vnp_SecureHash", "invalidsignaturexxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

    Map<String, String> result = vnPayService.processIpn(params);

    assertThat(result.get("RspCode")).isEqualTo("97");
    verify(transactionRepo, never()).save(any());
    verify(eventPublisher, never()).publishPaymentEvent(anyString(), any());
  }

  @Test
  @DisplayName("IPN với vnp_Amount lệch số tiền → RspCode 04, không PAID")
  void ipn_amountMismatch_returns04() {
    // Session amount = 100k VND, IPN gửi 200k VND (lệch)
    Map<String, String> params = buildIpnParams("00", 200_000L);
    when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(mockSession())); // session = 100k

    Map<String, String> result = vnPayService.processIpn(params);

    assertThat(result.get("RspCode")).isEqualTo("04");
    verify(transactionRepo, never()).save(any());
    verify(eventPublisher, never()).publishPaymentEvent(anyString(), any());
  }

  @Test
  @DisplayName("processIpn response map chứa RspCode và Message (không bị envelope bọc)")
  void ipn_responseMap_hasRspCodeAndMessage() {
    Map<String, String> params = buildIpnParams("00", AMOUNT_VND);
    when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(mockSession()));
    when(transactionRepo.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
    when(transactionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, String> result = vnPayService.processIpn(params);

    // Map thuần — chỉ có RspCode + Message, không có "data" field
    assertThat(result).containsKey("RspCode");
    assertThat(result).containsKey("Message");
    assertThat(result).doesNotContainKey("data");
    assertThat(result).doesNotContainKey("status");
    assertThat(result.get("RspCode")).isEqualTo("00");
  }
}
