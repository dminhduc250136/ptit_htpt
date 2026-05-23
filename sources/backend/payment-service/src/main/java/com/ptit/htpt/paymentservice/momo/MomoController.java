package com.ptit.htpt.paymentservice.momo;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MoMo callback endpoints.
 *
 * Cả 2 endpoint PHẢI bypass ApiResponseAdvice (D-13):
 *   - /momo/ipn: MoMo yêu cầu trả HTTP 204 No Content (D-12)
 *   - /momo/return: FE đọc trực tiếp, không cần ApiResponse envelope
 *
 * Bypass đạt bằng cách thêm prefix "/payments/momo" vào SKIP_PREFIXES
 * của ApiResponseAdvice.java.
 *
 * Cả 2 endpoint truy cập được không cần JWT (gateway whitelist — T-26.1-06).
 * Bảo mật dựa hoàn toàn vào HMAC SHA256 verify trong MomoService.
 */
@RestController
@RequestMapping("/payments")
public class MomoController {

  private final MomoService momoService;

  public MomoController(MomoService momoService) {
    this.momoService = momoService;
  }

  /**
   * POST /payments/momo/ipn — IPN server-to-server từ MoMo.
   * Luôn trả HTTP 204 No Content (D-12) để MoMo không retry.
   * Logic verify + update trong MomoService.processIpn.
   */
  @PostMapping("/momo/ipn")
  public ResponseEntity<Void> ipn(@RequestBody Map<String, Object> payload) {
    momoService.processIpn(payload);
    return ResponseEntity.noContent().build();
  }

  /**
   * GET /payments/momo/return — browser redirect sau khi khách thanh toán trên MoMo.
   * CHỈ verify chữ ký để hiển thị, KHÔNG update DB (T-26.1-04).
   * Trả JSON với {valid, orderId, resultCode, paymentTransactionNo} cho FE đọc.
   */
  @GetMapping("/momo/return")
  public Map<String, Object> momoReturn(@RequestParam Map<String, String> params) {
    return momoService.buildReturnView(params);
  }
}
