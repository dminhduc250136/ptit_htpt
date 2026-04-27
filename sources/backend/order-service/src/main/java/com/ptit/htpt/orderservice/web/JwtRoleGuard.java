package com.ptit.htpt.orderservice.web;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 9 / Plan 09-02 (UI-02 backend, D-05 REVISED).
 *
 * Manual JWT role check — KHÔNG dùng @PreAuthorize (codebase chưa setup Spring Security).
 * Dùng cho stats endpoints admin-only. Spring Security setup defer cho future hardening.
 *
 * Behavior:
 * - Header thiếu hoặc không phải Bearer → 401 UNAUTHORIZED "Missing or invalid Authorization header"
 * - Token invalid/expired → 401 UNAUTHORIZED "Invalid token"
 * - Token valid nhưng roles claim không chứa ADMIN → 403 FORBIDDEN "ADMIN role required"
 */
@Component
public class JwtRoleGuard {

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }

  public void requireAdmin(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
          "Missing or invalid Authorization header");
    }
    String token = authorizationHeader.substring("Bearer ".length()).trim();
    Claims claims;
    try {
      claims = Jwts.parser()
          .verifyWith(getSigningKey())
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
    }
    Object rolesClaim = claims.get("roles");
    String roles = rolesClaim == null ? "" : rolesClaim.toString();
    // Roles shape: "USER" | "ADMIN" | "ADMIN,USER" — split bằng comma
    boolean isAdmin = false;
    for (String r : roles.split(",")) {
      if ("ADMIN".equals(r.trim())) {
        isAdmin = true;
        break;
      }
    }
    if (!isAdmin) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role required");
    }
  }
}
