package com.ptit.htpt.apigateway.auth;

/**
 * Phase 25: token JWT hợp lệ về chữ ký nhưng đã hết hạn (exp claim).
 * Tách riêng khỏi {@link TokenInvalidException} để gateway trả mã lỗi
 * AUTH_TOKEN_EXPIRED — frontend dùng mã này để redirect login.
 */
public class TokenExpiredException extends RuntimeException {
  public TokenExpiredException(String message, Throwable cause) {
    super(message, cause);
  }
}
