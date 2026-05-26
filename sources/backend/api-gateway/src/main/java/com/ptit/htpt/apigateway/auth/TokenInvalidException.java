package com.ptit.htpt.apigateway.auth;

/**
 * Phase 25: token JWT không hợp lệ — malformed, sai chữ ký, thiếu claim
 * bắt buộc, hoặc bất kỳ lỗi parse nào khác (trừ hết hạn).
 * Gateway trả mã lỗi AUTH_TOKEN_INVALID.
 */
public class TokenInvalidException extends RuntimeException {
  public TokenInvalidException(String message, Throwable cause) {
    super(message, cause);
  }

  public TokenInvalidException(String message) {
    super(message);
  }
}
