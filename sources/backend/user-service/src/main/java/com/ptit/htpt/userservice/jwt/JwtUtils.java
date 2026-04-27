package com.ptit.htpt.userservice.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Phase 6 / Plan 01 (AUTH-01, AUTH-02): JWT issue + parse dùng JJWT 0.12.x API.
 *
 * API notes (JJWT 0.12.x — KHÔNG dùng 0.11.x deprecated API):
 * - Builder: Jwts.builder() + .signWith(key, Jwts.SIG.HS256)
 * - Parser: Jwts.parser().verifyWith(key).build() (KHÔNG dùng Jwts.parserBuilder())
 *
 * Claims: sub=userId, username, roles, iat, exp (24h default).
 * Secret: ${JWT_SECRET} env var; fallback dev value 32+ chars để tránh WeakKeyException.
 */
@Component
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Phát hành JWT HS256 với claims: sub=userId, username, name, roles.
     *
     * @param userId   UUID string của user
     * @param username username của user
     * @param fullName tên đầy đủ của user (có thể null — fallback về username, D-10)
     * @param roles    roles string (ví dụ: "USER" hoặc "ADMIN")
     * @return JWT compact string
     */
    public String issueToken(String userId, String username, String fullName, String roles) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId)
            .claim("username", username)
            .claim("name", (fullName != null && !fullName.isBlank()) ? fullName : username)  // D-10: fallback về username
            .claim("roles", roles)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(getSigningKey(), Jwts.SIG.HS256)
            .compact();
    }

    /**
     * Parse và verify JWT token.
     *
     * @param token JWT compact string
     * @return Claims nếu valid
     * @throws io.jsonwebtoken.JwtException nếu token invalid hoặc expired
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
