package com.ptit.htpt.apigateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.htpt.apigateway.auth.JwtVerifier.VerifiedClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * Phase 25 Plan 01: unit test cho {@link JwtVerifier}.
 *
 * <p>Token test được phát hành tại chỗ bằng JJWT với cùng secret, mô phỏng
 * token do user-service issue (claims sub / username / roles).
 */
class JwtVerifierTest {

  private static final String SECRET =
      "dev-jwt-secret-key-minimum-32-characters-for-hs256-ok";
  private static final SecretKey KEY =
      Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

  private final JwtVerifier verifier = new JwtVerifier(SECRET);

  private static String issue(String userId, String username, String roles, long ttlMs) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId)
        .claim("username", username)
        .claim("roles", roles)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(ttlMs)))
        .signWith(KEY, Jwts.SIG.HS256)
        .compact();
  }

  @Test
  void verify_parsesValidToken() {
    String token = issue("user-123", "alice", "USER,ADMIN", 60_000);

    VerifiedClaims claims = verifier.verify(token);

    assertThat(claims.userId()).isEqualTo("user-123");
    assertThat(claims.username()).isEqualTo("alice");
    assertThat(claims.roles()).containsExactly("USER", "ADMIN");
  }

  @Test
  void verify_throwsTokenExpired_forExpiredToken() {
    String token = issue("user-123", "alice", "USER", -1_000);

    assertThatThrownBy(() -> verifier.verify(token))
        .isInstanceOf(TokenExpiredException.class);
  }

  @Test
  void verify_throwsTokenInvalid_forMalformedToken() {
    assertThatThrownBy(() -> verifier.verify("not-a-real-jwt"))
        .isInstanceOf(TokenInvalidException.class);
  }

  @Test
  void verify_throwsTokenInvalid_forWrongSignature() {
    SecretKey otherKey =
        Keys.hmacShaKeyFor(
            "another-secret-key-with-at-least-32-characters-xx".getBytes(StandardCharsets.UTF_8));
    String token =
        Jwts.builder()
            .subject("user-123")
            .expiration(Date.from(Instant.now().plusMillis(60_000)))
            .signWith(otherKey, Jwts.SIG.HS256)
            .compact();

    assertThatThrownBy(() -> verifier.verify(token))
        .isInstanceOf(TokenInvalidException.class);
  }
}
