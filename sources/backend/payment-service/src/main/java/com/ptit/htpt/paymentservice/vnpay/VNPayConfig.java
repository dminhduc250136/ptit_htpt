package com.ptit.htpt.paymentservice.vnpay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình VNPay sandbox đọc từ env vars.
 * KHÔNG hardcode secret, KHÔNG log hashSecret (T-26-05).
 *
 * Env vars cần thiết:
 *   VNP_TMN_CODE     — merchant terminal code
 *   VNP_HASH_SECRET  — HMAC SHA512 secret key
 *   VNP_PAY_URL      — VNPay payment URL endpoint
 *   VNP_RETURN_URL   — FE result page URL
 *   VNP_IPN_URL      — public IPN URL (cần tunnel nếu local)
 */
@ConfigurationProperties(prefix = "vnpay")
public record VNPayConfig(
    String tmnCode,
    String hashSecret,
    String payUrl,
    String returnUrl,
    String ipnUrl
) {
}
