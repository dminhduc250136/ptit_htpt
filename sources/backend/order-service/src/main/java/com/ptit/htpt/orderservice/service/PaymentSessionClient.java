package com.ptit.htpt.orderservice.service;

import com.ptit.htpt.orderservice.api.ApiResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 26 / Plan 26-03 (D-04, T-26-10): cross-svc REST client gọi payment-service qua gateway
 * tạo payment session và lấy về paymentUrl.
 *
 * <p>Phase 26.1 / Plan 26.1-02 (D-08): đổi provider "VNPAY" → "MOMO" + rename method.
 * URL endpoint giữ nguyên (chung cho mọi provider): http://api-gateway:8080/api/payments/sessions.
 *
 * <p>Copy cấu trúc {@code ProductBatchClient}: {@code @Component} + {@code RestTemplate} injected.
 * URL qua gateway (http://api-gateway:8080) + forward Bearer JWT của user gốc (T-26-10).
 *
 * <p>KHÁC ProductBatchClient: fail → KHÔNG fallback empty — paymentUrl bắt buộc cho đơn MOMO
 * → throw {@link ResponseStatusException}(502 BAD_GATEWAY).
 */
@Component
public class PaymentSessionClient {

  private static final Logger log = LoggerFactory.getLogger(PaymentSessionClient.class);
  private static final String URL = "http://api-gateway:8080/api/payments/sessions";

  private final RestTemplate restTemplate;

  public PaymentSessionClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  /**
   * Tạo MoMo session tại payment-service. Trả paymentUrl để redirect user.
   *
   * <p>Phase 26.1 (D-08): renamed từ createVNPaySession; đổi provider "VNPAY" → "MOMO".
   * Logic REST và URL endpoint giữ nguyên 100% (payment-service route theo provider value).
   *
   * @param orderId     order id (UUID string)
   * @param amountVnd   số tiền đơn hàng tính bằng VND (đã trừ discount)
   * @param orderInfo   mô tả đơn hàng (vd: mã đơn)
   * @param authHeader  giá trị header Authorization từ request gốc (Bearer token) — T-26-10 forward JWT
   * @return paymentUrl (non-null) để FE redirect sang cổng MoMo
   * @throws ResponseStatusException 502 nếu payment-service không trả về paymentUrl
   */
  public String createMomoSession(String orderId, long amountVnd, String orderInfo,
                                  String authHeader) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      // T-26-10: forward Bearer JWT của user gốc — payment-service verify qua gateway auth
      if (authHeader != null) {
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
      }
      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
          Map.of(
              "provider", "MOMO",
              "orderId", orderId,
              "amount", amountVnd,
              "orderInfo", orderInfo
          ),
          headers
      );

      ResponseEntity<ApiResponse<Map<String, Object>>> resp = restTemplate.exchange(
          URL, HttpMethod.POST, entity,
          new ParameterizedTypeReference<>() {});

      ApiResponse<Map<String, Object>> body = resp.getBody();
      if (body == null || body.data() == null) {
        log.error("[MOMO-SESSION] payment-service trả null body cho orderId={}", orderId);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Không tạo được phiên thanh toán MoMo");
      }
      Object paymentUrl = body.data().get("paymentUrl");
      if (paymentUrl == null || paymentUrl.toString().isBlank()) {
        log.error("[MOMO-SESSION] payment-service không trả paymentUrl cho orderId={}", orderId);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Không tạo được phiên thanh toán MoMo");
      }
      log.info("[MOMO-SESSION] orderId={} paymentUrl=OK", orderId);
      return paymentUrl.toString();
    } catch (ResponseStatusException e) {
      throw e; // re-throw — đã log ở trên
    } catch (Exception ex) {
      log.error("[MOMO-SESSION] lỗi khi gọi payment-service orderId={}: {}", orderId,
          ex.getMessage());
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Không tạo được phiên thanh toán MoMo");
    }
  }
}
