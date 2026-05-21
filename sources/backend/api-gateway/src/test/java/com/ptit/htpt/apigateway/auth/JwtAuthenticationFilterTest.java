package com.ptit.htpt.apigateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.htpt.apigateway.auth.AuthProperties.PublicEndpoint;
import com.ptit.htpt.apigateway.gateway.ApiErrorResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Phase 25 Plan 04: integration test cho {@link JwtAuthenticationFilter}.
 *
 * <p>Filter được test trực tiếp với một {@link GatewayFilterChain} giả lập:
 * chain capture lại exchange đã mutate để assert header downstream. Token test
 * phát hành tại chỗ bằng JJWT (cùng secret), mô phỏng token user-service issue.
 *
 * <p>7 case theo 25-CONTEXT.md D-20.
 */
class JwtAuthenticationFilterTest {

  private static final String SECRET =
      "dev-jwt-secret-key-minimum-32-characters-for-hs256-ok";
  private static final SecretKey KEY =
      Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

  private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    JwtVerifier verifier = new JwtVerifier(SECRET);
    AuthProperties props = new AuthProperties();
    props.setPublicEndpoints(
        List.of(
            new PublicEndpoint(HttpMethod.GET, "/api/products"),
            new PublicEndpoint(HttpMethod.GET, "/api/products/**"),
            new PublicEndpoint(HttpMethod.POST, "/api/users/auth/login")));
    props.setAdminPathPatterns(List.of("/api/*/admin/**", "/api/admin/**"));
    AuthErrorResponseWriter writer =
        new AuthErrorResponseWriter(new ObjectMapper().findAndRegisterModules());
    filter = new JwtAuthenticationFilter(verifier, props, writer);
  }

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

  /** Chain giả lập: capture exchange đã route tới downstream. */
  private static final class CapturingChain implements GatewayFilterChain {
    private final AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();

    @Override
    public Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange) {
      captured.set(exchange.getRequest());
      return Mono.empty();
    }

    ServerHttpRequest downstreamRequest() {
      return captured.get();
    }
  }

  private ApiErrorResponse readError(MockServerWebExchange exchange) {
    String json = exchange.getResponse().getBodyAsString().block();
    try {
      return new ObjectMapper()
          .findAndRegisterModules()
          .readValue(json, ApiErrorResponse.class);
    } catch (Exception e) {
      throw new AssertionError("không parse được body lỗi: " + json, e);
    }
  }

  // 1 — Public endpoint không Bearer → pass, downstream KHÔNG có X-User-Id.
  @Test
  void publicEndpoint_noToken_passesWithoutUserHeader() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/products"));
    CapturingChain chain = new CapturingChain();

    filter.filter(exchange, chain).block();

    assertThat(chain.downstreamRequest()).isNotNull();
    assertThat(chain.downstreamRequest().getHeaders().getFirst("X-User-Id")).isNull();
  }

  // 2 — Public endpoint Bearer hợp lệ → pass + inject X-User-Id.
  @Test
  void publicEndpoint_validToken_injectsUserHeader() {
    String token = issue("user-aaa", "alice", "USER", 60_000);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    CapturingChain chain = new CapturingChain();

    filter.filter(exchange, chain).block();

    assertThat(chain.downstreamRequest().getHeaders().getFirst("X-User-Id"))
        .isEqualTo("user-aaa");
  }

  // 3 — Protected endpoint không Bearer → 401 AUTH_TOKEN_MISSING.
  @Test
  void protectedEndpoint_noToken_returns401Missing() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));

    filter.filter(exchange, new CapturingChain()).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(readError(exchange).code()).isEqualTo("AUTH_TOKEN_MISSING");
  }

  // 4 — Protected endpoint Bearer rác → 401 AUTH_TOKEN_INVALID.
  @Test
  void protectedEndpoint_invalidToken_returns401Invalid() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"));

    filter.filter(exchange, new CapturingChain()).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(readError(exchange).code()).isEqualTo("AUTH_TOKEN_INVALID");
  }

  // 5 — Protected endpoint Bearer hết hạn → 401 AUTH_TOKEN_EXPIRED.
  @Test
  void protectedEndpoint_expiredToken_returns401Expired() {
    String token = issue("user-aaa", "alice", "USER", -1_000);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    filter.filter(exchange, new CapturingChain()).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(readError(exchange).code()).isEqualTo("AUTH_TOKEN_EXPIRED");
  }

  // 6 — Admin endpoint với role USER → 403 AUTH_ROLE_DENIED.
  @Test
  void adminEndpoint_userRole_returns403() {
    String token = issue("user-aaa", "alice", "USER", 60_000);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users/admin/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    filter.filter(exchange, new CapturingChain()).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(readError(exchange).code()).isEqualTo("AUTH_ROLE_DENIED");
  }

  // 7 — Client gửi X-User-Id giả + Bearer hợp lệ → downstream nhận sub của token.
  @Test
  void forgedUserIdHeader_isStrippedAndReplacedByTokenSub() {
    String token = issue("real-user-id", "bob", "USER", 60_000);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "forged-victim-id")
                .header("X-User-Roles", "ADMIN"));
    CapturingChain chain = new CapturingChain();

    filter.filter(exchange, chain).block();

    HttpHeaders downstream = chain.downstreamRequest().getHeaders();
    assertThat(downstream.getFirst("X-User-Id")).isEqualTo("real-user-id");
    assertThat(downstream.getFirst("X-User-Roles")).isEqualTo("USER");
  }

  // Bonus — admin endpoint với role ADMIN → pass.
  @Test
  void adminEndpoint_adminRole_passes() {
    String token = issue("admin-id", "root", "USER,ADMIN", 60_000);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users/admin/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    CapturingChain chain = new CapturingChain();

    filter.filter(exchange, chain).block();

    assertThat(chain.downstreamRequest().getHeaders().getFirst("X-User-Id"))
        .isEqualTo("admin-id");
  }
}
