package com.ptit.htpt.paymentservice.momo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình MoMo Developer Sandbox đọc từ env vars.
 * KHÔNG hardcode secretKey, KHÔNG log secretKey (T-26.1-05).
 *
 * Env vars cần thiết:
 *   MOMO_PARTNER_CODE  — partner code
 *   MOMO_ACCESS_KEY    — access key
 *   MOMO_SECRET_KEY    — HMAC SHA256 secret key
 *   MOMO_PAY_URL       — MoMo create endpoint
 *   MOMO_RETURN_URL    — FE result page URL (redirectUrl)
 *   MOMO_IPN_URL       — public IPN URL (cần tunnel nếu local)
 */
@ConfigurationProperties(prefix = "momo")
public record MomoConfig(
    String partnerCode,
    String accessKey,
    String secretKey,
    String payUrl,
    String returnUrl,
    String ipnUrl
) {
}
