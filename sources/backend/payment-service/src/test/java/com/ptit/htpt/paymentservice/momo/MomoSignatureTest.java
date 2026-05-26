package com.ptit.htpt.paymentservice.momo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test MomoSignature — không cần Spring context.
 * MomoConfig tạo thủ công với sandbox public credentials (D-18).
 */
class MomoSignatureTest {

  // Sandbox public credentials (D-18)
  private static final MomoConfig CFG = new MomoConfig(
      "MOMO",
      "F8BBA842ECF85",
      "K951B6PE1waDMi640xX08PD3vg6EkVlz",
      "https://test-payment.momo.vn/v2/gateway/api/create",
      "http://localhost:3000/checkout/result",
      "https://example.test/api/payments/momo/ipn"
  );

  private final MomoSignature sig = new MomoSignature(CFG);

  @Test
  @DisplayName("hmacSHA256RoundTrip: sign(data) trả hex 64 ký tự lowercase")
  void hmacSha256RoundTrip() {
    String result = sig.sign("hello");
    // HmacSHA256 luôn ra 32 bytes = 64 hex chars
    assertThat(result).hasSize(64);
    assertThat(result).matches("[0-9a-f]{64}");
    // Verify deterministic: cùng input → cùng output
    assertThat(result).isEqualTo(sig.sign("hello"));
    // Verify đúng: dùng static helper để cross-check
    String expected = MomoSignature.hmacSHA256("K951B6PE1waDMi640xX08PD3vg6EkVlz", "hello");
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("verifyIpnAcceptsValidSignature: build payload, tính sig, verify true")
  void verifyIpnAcceptsValidSignature() {
    Map<String, Object> payload = buildSampleIpnPayload("0", 100000L, "session-001");
    // Tính signature thật
    String signData = sig.buildSignDataIpn(payload);
    String signature = sig.sign(signData);
    payload.put("signature", signature);

    assertThat(sig.verifyIpn(payload)).isTrue();
  }

  @Test
  @DisplayName("verifyIpnRejectsTamperedSignature: sửa 1 ký tự signature → false")
  void verifyIpnRejectsTamperedSignature() {
    Map<String, Object> payload = buildSampleIpnPayload("0", 100000L, "session-002");
    String signData = sig.buildSignDataIpn(payload);
    String signature = sig.sign(signData);
    // Tamper: đổi ký tự cuối
    String tamperedSig = signature.substring(0, signature.length() - 1)
        + (signature.endsWith("a") ? "b" : "a");
    payload.put("signature", tamperedSig);

    assertThat(sig.verifyIpn(payload)).isFalse();
  }

  @Test
  @DisplayName("buildSignDataCreateUsesFixedOrder: thứ tự cố định theo MoMo doc, KHÔNG sort key")
  void buildSignDataCreateUsesFixedOrder() {
    String requestId = "req-001";
    long amount = 100000L;
    String extraData = "eyJvcmRlcklkIjoib3JkLTAwMSJ9"; // base64 cố định
    String orderId = "req-001"; // orderId = requestId = paymentSessionId
    String orderInfo = "Thanh toan don ord-001";
    String requestType = "payWithMethod";

    String signData = sig.buildSignDataCreate(requestId, amount, extraData, orderId, orderInfo, requestType);

    String expected = "accessKey=F8BBA842ECF85"
        + "&amount=100000"
        + "&extraData=eyJvcmRlcklkIjoib3JkLTAwMSJ9"
        + "&ipnUrl=https://example.test/api/payments/momo/ipn"
        + "&orderId=req-001"
        + "&orderInfo=Thanh toan don ord-001"
        + "&partnerCode=MOMO"
        + "&redirectUrl=http://localhost:3000/checkout/result"
        + "&requestId=req-001"
        + "&requestType=payWithMethod";

    assertThat(signData).isEqualTo(expected);
  }

  @Test
  @DisplayName("buildSignDataIpnUsesLexicographicOrder: 13 field theo đúng thứ tự MoMo IPN spec")
  void buildSignDataIpnUsesLexicographicOrder() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("accessKey", "F8BBA842ECF85");
    payload.put("amount", "100000");
    payload.put("extraData", "eyJvcmRlcklkIjoib3JkLTAwMSJ9");
    payload.put("message", "Successful.");
    payload.put("orderId", "session-001");
    payload.put("orderInfo", "Thanh toan don ord-001");
    payload.put("orderType", "momo_wallet");
    payload.put("partnerCode", "MOMO");
    payload.put("payType", "qr");
    payload.put("requestId", "session-001");
    payload.put("responseTime", "1716441600000");
    payload.put("resultCode", "0");
    payload.put("transId", "3498980476");
    // Thêm signature field thừa — không được đưa vào sign data
    payload.put("signature", "irrelevant-value");

    String signData = sig.buildSignDataIpn(payload);

    String expected = "accessKey=F8BBA842ECF85"
        + "&amount=100000"
        + "&extraData=eyJvcmRlcklkIjoib3JkLTAwMSJ9"
        + "&message=Successful."
        + "&orderId=session-001"
        + "&orderInfo=Thanh toan don ord-001"
        + "&orderType=momo_wallet"
        + "&partnerCode=MOMO"
        + "&payType=qr"
        + "&requestId=session-001"
        + "&responseTime=1716441600000"
        + "&resultCode=0"
        + "&transId=3498980476";

    assertThat(signData).isEqualTo(expected);
    // Quan trọng: "signature" KHÔNG xuất hiện trong sign data
    assertThat(signData).doesNotContain("signature");
  }

  // Helper: build IPN payload cơ bản
  private Map<String, Object> buildSampleIpnPayload(String resultCode, long amount, String requestId) {
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
    return payload;
  }
}
