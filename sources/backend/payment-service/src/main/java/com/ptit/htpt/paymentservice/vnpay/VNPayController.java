package com.ptit.htpt.paymentservice.vnpay;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * VNPay callback endpoints.
 *
 * Cả 2 endpoint PHẢI bypass ApiResponseAdvice (D-06, PATTERNS §VNPayController):
 *   - /vnpay/ipn: VNPay yêu cầu JSON thuần {"RspCode","Message"}
 *   - /vnpay/return: FE đọc trực tiếp, không cần envelope
 *
 * Bypass đạt bằng cách thêm prefix "/payments/vnpay" vào SKIP_PREFIXES
 * của ApiResponseAdvice.java (đã sửa trong task này).
 *
 * Cả 2 endpoint là GET, truy cập được không cần JWT (gateway whitelist — T-26-06, Pitfall 4).
 * Bảo mật dựa hoàn toàn vào verify vnp_SecureHash trong VNPayService.
 */
@RestController
@RequestMapping("/payments")
public class VNPayController {

  private final VNPayService vnPayService;

  public VNPayController(VNPayService vnPayService) {
    this.vnPayService = vnPayService;
  }

  /**
   * GET /payments/vnpay/ipn — IPN server-to-server từ VNPay.
   * Trả JSON thuần {"RspCode","Message"} — KHÔNG wrap ApiResponse.
   * VNPay retry nếu không nhận được 200 + RspCode 00 hoặc 02.
   */
  @GetMapping("/vnpay/ipn")
  public Map<String, String> ipn(@RequestParam Map<String, String> params) {
    return vnPayService.processIpn(params);
  }

  /**
   * GET /payments/vnpay/return — browser redirect sau khi khách thanh toán.
   * CHỈ verify chữ ký để hiển thị, KHÔNG update DB (D-06, T-26-04).
   * Trả JSON với {valid, orderId, responseCode, vnpTransactionNo} cho FE đọc.
   */
  @GetMapping("/vnpay/return")
  public Map<String, Object> returnCallback(@RequestParam Map<String, String> params) {
    return vnPayService.buildReturnView(params);
  }
}
