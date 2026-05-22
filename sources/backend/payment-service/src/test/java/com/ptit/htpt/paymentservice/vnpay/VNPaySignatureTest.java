package com.ptit.htpt.paymentservice.vnpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test VNPaySignature — không cần Spring context.
 * VNPayConfig tạo thủ công với hash-secret cố định.
 */
class VNPaySignatureTest {

  private static final String TEST_SECRET = "SANDBOXSECRETKEY";
  private static final String TEST_TMN_CODE = "TESTCODE";
  private static final String TEST_PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
  private static final String TEST_RETURN_URL = "http://localhost:3000/checkout/result";

  private VNPaySignature signature;

  @BeforeEach
  void setUp() {
    VNPayConfig cfg = new VNPayConfig(TEST_TMN_CODE, TEST_SECRET, TEST_PAY_URL, TEST_RETURN_URL, "");
    signature = new VNPaySignature(cfg);
  }

  @Test
  @DisplayName("verifySignature: ký params cố định rồi verify → true")
  void verifySignature_withValidHash_returnsTrue() {
    // Chuẩn bị map params cố định
    Map<String, String> params = new TreeMap<>();
    params.put("vnp_TmnCode", TEST_TMN_CODE);
    params.put("vnp_Amount", "100000");
    params.put("vnp_TxnRef", "test-session-001");
    params.put("vnp_OrderInfo", "Don hang test");
    params.put("vnp_ResponseCode", "00");

    // Tính hash từ params
    StringBuilder hashData = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> e : params.entrySet()) {
      if (!first) hashData.append('&');
      hashData.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII))
          .append('=')
          .append(URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII));
      first = false;
    }
    String hash = VNPaySignature.hmacSHA512(TEST_SECRET, hashData.toString());

    // Thêm hash vào params rồi verify
    Map<String, String> paramsWithHash = new HashMap<>(params);
    paramsWithHash.put("vnp_SecureHash", hash);

    assertThat(signature.verifySignature(paramsWithHash)).isTrue();
  }

  @Test
  @DisplayName("verifySignature: hash bị sửa 1 ký tự → false")
  void verifySignature_withTamperedHash_returnsFalse() {
    Map<String, String> params = new TreeMap<>();
    params.put("vnp_TmnCode", TEST_TMN_CODE);
    params.put("vnp_Amount", "100000");
    params.put("vnp_TxnRef", "test-session-002");
    params.put("vnp_OrderInfo", "Don hang test");

    StringBuilder hashData = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> e : params.entrySet()) {
      if (!first) hashData.append('&');
      hashData.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII))
          .append('=')
          .append(URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII));
      first = false;
    }
    String hash = VNPaySignature.hmacSHA512(TEST_SECRET, hashData.toString());

    // Sửa 1 ký tự trong hash
    String tamperedHash = hash.substring(0, hash.length() - 1) + (hash.endsWith("a") ? "b" : "a");

    Map<String, String> paramsWithTamperedHash = new HashMap<>(params);
    paramsWithTamperedHash.put("vnp_SecureHash", tamperedHash);

    assertThat(signature.verifySignature(paramsWithTamperedHash)).isFalse();
  }

  @Test
  @DisplayName("verifySignature: loại vnp_SecureHash + vnp_SecureHashType trước khi hash")
  void verifySignature_excludesSecureHashFields_fromSigning() {
    Map<String, String> params = new TreeMap<>();
    params.put("vnp_TmnCode", TEST_TMN_CODE);
    params.put("vnp_Amount", "200000");
    params.put("vnp_TxnRef", "test-session-003");

    StringBuilder hashData = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> e : params.entrySet()) {
      if (!first) hashData.append('&');
      hashData.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII))
          .append('=')
          .append(URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII));
      first = false;
    }
    String hash = VNPaySignature.hmacSHA512(TEST_SECRET, hashData.toString());

    // Thêm vnp_SecureHash + vnp_SecureHashType — verify vẫn phải đúng
    Map<String, String> paramsWithMeta = new HashMap<>(params);
    paramsWithMeta.put("vnp_SecureHash", hash);
    paramsWithMeta.put("vnp_SecureHashType", "SHA512");

    // Nếu verify không loại 2 field này → hash sẽ khác → false (lỗi)
    assertThat(signature.verifySignature(paramsWithMeta)).isTrue();
  }

  @Test
  @DisplayName("buildPaymentUrl: vnp_Amount = amountVnd × 100")
  void buildPaymentUrl_setsAmountMultipliedBy100() {
    long amountVnd = 100_000L; // 100,000 VND
    String url = signature.buildPaymentUrl("session-001", amountVnd, "Don hang 001", "127.0.0.1");

    // URL phải chứa vnp_Amount=10000000 (100000 × 100)
    assertThat(url).contains("vnp_Amount=10000000");
  }

  @Test
  @DisplayName("buildPaymentUrl: hash dùng URLEncoder.encode US_ASCII cho cả key và value")
  void buildPaymentUrl_usesUsAsciiEncoding() {
    // OrderInfo chứa khoảng trắng — phải encode thành %20 hoặc + tùy URLEncoder
    // Quan trọng hơn: URL phải hợp lệ và có vnp_SecureHash
    String url = signature.buildPaymentUrl("session-002", 50_000L, "Don hang co ky tu", "192.168.1.1");

    assertThat(url).contains("vnp_SecureHash=");
    // vnp_OrderInfo phải được encode (khoảng trắng → + hay %20)
    assertThat(url).doesNotContain("vnp_OrderInfo=Don hang co ky tu"); // phải encoded
    // Verify rằng URL có thể parse lại và verify chữ ký (round-trip)
    // Tách query string
    String queryPart = url.substring(url.indexOf('?') + 1);
    Map<String, String> parsed = new TreeMap<>();
    for (String pair : queryPart.split("&")) {
      int idx = pair.indexOf('=');
      if (idx > 0) {
        String key = pair.substring(0, idx);
        String value = pair.substring(idx + 1);
        // Decode để lấy giá trị thực
        try {
          parsed.put(java.net.URLDecoder.decode(key, StandardCharsets.UTF_8),
              java.net.URLDecoder.decode(value, StandardCharsets.UTF_8));
        } catch (Exception e) {
          parsed.put(key, value);
        }
      }
    }
    // Verify chữ ký từ URL đã build
    assertThat(signature.verifySignature(parsed)).isTrue();
  }
}
