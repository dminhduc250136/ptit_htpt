package com.ptit.htpt.paymentservice.vnpay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * VNPay HMAC SHA512 ký/verify theo spec v2.1.0.
 *
 * Điểm chí mạng (RESEARCH §Pitfall 1, 2):
 *   - vnp_Amount = amountVnd × 100 (Pitfall 2)
 *   - URLEncoder.encode(v, US_ASCII) cho CẢ ký lẫn verify (Pitfall 1)
 *   - verify loại vnp_SecureHash + vnp_SecureHashType trước khi hash (Pitfall 1)
 *   - so sánh equalsIgnoreCase (case-insensitive)
 *
 * Security: KHÔNG log cfg.hashSecret() (T-26-05).
 */
@Component
public class VNPaySignature {

  private final VNPayConfig cfg;

  public VNPaySignature(VNPayConfig cfg) {
    this.cfg = cfg;
  }

  /**
   * Build VNPay payment URL có chữ ký HMAC SHA512.
   *
   * @param txnRef    payment session ID (UUID — cho phép retry, Pitfall 6)
   * @param amountVnd số tiền VND (CHƯA nhân 100 — method này sẽ nhân)
   * @param orderInfo mô tả đơn (ASCII, không dấu đặc biệt)
   * @param clientIp  IP khách
   * @return payment URL đầy đủ tới cổng sandbox
   */
  public String buildPaymentUrl(String txnRef, long amountVnd, String orderInfo, String clientIp) {
    Map<String, String> p = new TreeMap<>(); // TreeMap → tự sort tăng dần theo key
    p.put("vnp_Version", "2.1.0");
    p.put("vnp_Command", "pay");
    p.put("vnp_TmnCode", cfg.tmnCode());
    p.put("vnp_Amount", String.valueOf(amountVnd * 100));      // Pitfall 2: nhân 100
    p.put("vnp_CurrCode", "VND");
    p.put("vnp_TxnRef", txnRef);                               // Pitfall 6: dùng sessionId
    p.put("vnp_OrderInfo", orderInfo);
    p.put("vnp_OrderType", "other");
    p.put("vnp_Locale", "vn");
    p.put("vnp_ReturnUrl", cfg.returnUrl());
    p.put("vnp_IpAddr", clientIp);
    String now = LocalDateTime.now(ZoneId.of("Etc/GMT-7"))
        .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    p.put("vnp_CreateDate", now);
    p.put("vnp_ExpireDate", LocalDateTime.now(ZoneId.of("Etc/GMT-7"))
        .plusMinutes(15).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

    StringBuilder hashData = new StringBuilder();
    StringBuilder query = new StringBuilder();
    for (Iterator<Map.Entry<String, String>> it = p.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, String> e = it.next();
      String encKey = URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII);
      String encVal = URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII);
      hashData.append(encKey).append('=').append(encVal);
      query.append(encKey).append('=').append(encVal);
      if (it.hasNext()) {
        hashData.append('&');
        query.append('&');
      }
    }
    String secureHash = hmacSHA512(cfg.hashSecret(), hashData.toString());
    query.append("&vnp_SecureHash=").append(secureHash);
    return cfg.payUrl() + "?" + query;
  }

  /**
   * Verify chữ ký HMAC SHA512 của callback IPN/return.
   *
   * @param params query params từ VNPay (gồm vnp_SecureHash)
   * @return true nếu chữ ký hợp lệ
   */
  public boolean verifySignature(Map<String, String> params) {
    String received = params.get("vnp_SecureHash");
    if (received == null || received.isBlank()) {
      return false;
    }
    Map<String, String> signed = new TreeMap<>(params);
    signed.remove("vnp_SecureHash");       // Pitfall 1: phải loại trước khi hash
    signed.remove("vnp_SecureHashType");

    StringBuilder hashData = new StringBuilder();
    for (Iterator<Map.Entry<String, String>> it = signed.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, String> e = it.next();
      hashData.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII))
          .append('=')
          .append(URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII));
      if (it.hasNext()) {
        hashData.append('&');
      }
    }
    String computed = hmacSHA512(cfg.hashSecret(), hashData.toString());
    return computed.equalsIgnoreCase(received);
  }

  /**
   * Tính HMAC SHA512 hex lowercase.
   */
  static String hmacSHA512(String key, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA512");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
      byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException("HMAC SHA512 failed", ex);
    }
  }
}
