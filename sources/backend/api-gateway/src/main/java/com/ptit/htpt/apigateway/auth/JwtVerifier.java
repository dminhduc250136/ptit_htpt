package com.ptit.htpt.apigateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Phase 25: parse + verify JWT HS256 ở edge gateway.
 *
 * <p>Dùng cùng JJWT 0.12.x API và cùng JWT_SECRET với user-service (xem
 * user-service {@code JwtUtils}). Chỉ giữ verify path — KHÔNG có issue path,
 * vì gateway không phát hành token.
 *
 * <p>Claims chuẩn của user-service: {@code sub}=userId, {@code username},
 * {@code roles} (chuỗi CSV, ví dụ "USER" hoặc "ADMIN").
 */
@Component
public class JwtVerifier {

  private final SecretKey signingKey;

  public JwtVerifier(@Value("${app.jwt.secret}") String secret) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Parse và verify token. Thành công trả {@link VerifiedClaims}.
   *
   * @param token chuỗi JWT compact (đã bỏ tiền tố "Bearer ")
   * @throws TokenExpiredException nếu token hết hạn
   * @throws TokenInvalidException nếu token malformed / sai chữ ký / thiếu sub
   */
  public VerifiedClaims verify(String token) {
    Claims claims;
    try {
      claims =
          Jwts.parser()
              .verifyWith(signingKey)
              .build()
              .parseSignedClaims(token)
              .getPayload();
    } catch (ExpiredJwtException e) {
      throw new TokenExpiredException("Token JWT đã hết hạn", e);
    } catch (JwtException | IllegalArgumentException e) {
      throw new TokenInvalidException("Token JWT không hợp lệ", e);
    }

    String userId = claims.getSubject();
    if (userId == null || userId.isBlank()) {
      throw new TokenInvalidException("Token JWT thiếu claim sub (userId)");
    }
    String username = claims.get("username", String.class);
    List<String> roles = extractRoles(claims.get("roles"));
    return new VerifiedClaims(userId, username, roles);
  }

  /**
   * Roles claim có thể là chuỗi CSV ("USER", "USER,ADMIN") hoặc một mảng/list.
   * Chuẩn hoá thành {@code List<String>} đã trim, bỏ phần tử rỗng.
   */
  private static List<String> extractRoles(Object raw) {
    List<String> result = new ArrayList<>();
    if (raw == null) {
      return result;
    }
    if (raw instanceof Collection<?> collection) {
      for (Object item : collection) {
        addRole(result, String.valueOf(item));
      }
      return result;
    }
    for (String part : String.valueOf(raw).split(",")) {
      addRole(result, part);
    }
    return result;
  }

  private static void addRole(List<String> target, String role) {
    if (role != null) {
      String trimmed = role.trim();
      if (!trimmed.isEmpty()) {
        target.add(trimmed);
      }
    }
  }

  /** Kết quả verify thành công: thông tin user trích từ claims. */
  public record VerifiedClaims(String userId, String username, List<String> roles) {}
}
