package com.ptit.htpt.paymentservice.momo;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * MoMo HMAC SHA256 ký/verify.
 *
 * Spec (D-10):
 *   - Ký create: concat key=value KHÔNG sort, KHÔNG URL-encode, theo thứ tự cố định MoMo doc
 *   - Ký IPN verify: concat 13 field theo thứ tự lexicographic MoMo IPN spec
 *   - Algorithm: HmacSHA256, hex encoded lowercase
 *   - Amount: raw VND (KHÔNG nhân thêm — khác một số cổng khác)
 *
 * Security: KHÔNG log config.secretKey() (T-26.1-05).
 */
@Component
public class MomoSignature {

  private final MomoConfig config;

  public MomoSignature(MomoConfig config) {
    this.config = config;
  }

  /**
   * Build sign data cho create payment request.
   * Thứ tự CỐ ĐỊNH theo MoMo doc (D-10) — KHÔNG sort tự động.
   *
   * @param requestId   paymentSessionId (idempotency key, cũng là orderId trong MoMo)
   * @param amount      số tiền VND raw (KHÔNG ×100)
   * @param extraData   Base64(JSON) chứa orderId nội bộ để link-back (D-11)
   * @param orderId     unique orderId gửi sang MoMo (= requestId = paymentSessionId)
   * @param orderInfo   mô tả đơn hàng
   * @param requestType loại request (vd: "payWithMethod")
   * @return chuỗi data string để ký
   */
  public String buildSignDataCreate(String requestId, long amount, String extraData,
                                    String orderId, String orderInfo, String requestType) {
    return "accessKey=" + config.accessKey()
        + "&amount=" + amount
        + "&extraData=" + extraData
        + "&ipnUrl=" + config.ipnUrl()
        + "&orderId=" + orderId
        + "&orderInfo=" + orderInfo
        + "&partnerCode=" + config.partnerCode()
        + "&redirectUrl=" + config.returnUrl()
        + "&requestId=" + requestId
        + "&requestType=" + requestType;
  }

  /**
   * Build sign data cho IPN verify.
   * Thứ tự LEXICOGRAPHIC theo MoMo IPN spec (D-10) — 13 field hard-coded, KHÔNG sort runtime.
   *
   * @param ipnPayload raw IPN payload từ MoMo
   * @return chuỗi data string để tính lại HMAC và so sánh
   */
  public String buildSignDataIpn(Map<String, Object> ipnPayload) {
    // 13 field theo thứ tự lexicographic của MoMo IPN spec
    return "accessKey=" + getString(ipnPayload, "accessKey")
        + "&amount=" + getString(ipnPayload, "amount")
        + "&extraData=" + getString(ipnPayload, "extraData")
        + "&message=" + getString(ipnPayload, "message")
        + "&orderId=" + getString(ipnPayload, "orderId")
        + "&orderInfo=" + getString(ipnPayload, "orderInfo")
        + "&orderType=" + getString(ipnPayload, "orderType")
        + "&partnerCode=" + getString(ipnPayload, "partnerCode")
        + "&payType=" + getString(ipnPayload, "payType")
        + "&requestId=" + getString(ipnPayload, "requestId")
        + "&responseTime=" + getString(ipnPayload, "responseTime")
        + "&resultCode=" + getString(ipnPayload, "resultCode")
        + "&transId=" + getString(ipnPayload, "transId");
  }

  /**
   * Tính HMAC SHA256 hex lowercase với config.secretKey().
   *
   * @param data chuỗi data string đã build
   * @return hex lowercase 64 ký tự
   */
  public String sign(String data) {
    return hmacSHA256(config.secretKey(), data);
  }

  /**
   * Verify chữ ký IPN từ MoMo.
   * Tự loại field "signature" khỏi sign data (buildSignDataIpn không có field này).
   *
   * @param ipnPayload raw IPN payload từ MoMo (gồm cả "signature")
   * @return true nếu chữ ký hợp lệ
   */
  public boolean verifyIpn(Map<String, Object> ipnPayload) {
    Object expected = ipnPayload.get("signature");
    if (expected == null) {
      return false;
    }
    String actual = sign(buildSignDataIpn(ipnPayload));
    return String.valueOf(expected).equalsIgnoreCase(actual);
  }

  /**
   * Tính HMAC SHA256 hex lowercase.
   * Package-private để test có thể compute hash mà không cần Spring context.
   */
  static String hmacSHA256(String key, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException("HMAC SHA256 failed", ex);
    }
  }

  private String getString(Map<String, Object> map, String key) {
    return String.valueOf(map.getOrDefault(key, ""));
  }
}
