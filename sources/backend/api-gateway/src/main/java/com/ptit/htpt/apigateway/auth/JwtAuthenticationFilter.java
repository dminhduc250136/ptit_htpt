package com.ptit.htpt.apigateway.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.htpt.apigateway.auth.JwtVerifier.VerifiedClaims;
import com.ptit.htpt.apigateway.gateway.ApiErrorResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Phase 25: edge authentication filter cho API Gateway.
 *
 * <p>Trust boundary chuyển về gateway: filter này chạy sớm nhất (order -100,
 * trước RequestIdFilter) và đảm bảo mọi request downstream chỉ mang
 * {@code X-User-Id} / {@code X-User-Roles} do gateway inject từ JWT đã verify.
 *
 * <p>Luồng xử lý:
 *
 * <ol>
 *   <li>Strip vô điều kiện các header tin cậy do client tự gửi (D-06).
 *   <li>Parse Bearer token nếu có; verify chữ ký + hạn (D-05).
 *   <li>Endpoint protected mà không có token hợp lệ → 401.
 *   <li>Endpoint admin mà role không chứa ADMIN → 403.
 *   <li>Token hợp lệ → inject {@code X-User-Id} / {@code X-User-Roles} /
 *       {@code X-Username} đáng tin cậy (D-07).
 * </ol>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

  /** Header tin cậy bị strip khỏi request client trước khi route (D-06). */
  static final List<String> TRUSTED_HEADERS =
      List.of("X-User-Id", "X-User-Roles", "X-Username", "X-Request-Id");

  private final JwtVerifier verifier;
  private final AuthProperties props;
  private final ObjectMapper objectMapper;
  private final AntPathMatcher matcher = new AntPathMatcher();

  public JwtAuthenticationFilter(
      JwtVerifier verifier, AuthProperties props, ObjectMapper objectMapper) {
    this.verifier = verifier;
    this.props = props;
    this.objectMapper = objectMapper;
  }

  @Override
  public int getOrder() {
    // Chạy trước RequestIdFilter (HIGHEST_PRECEDENCE) — JWT verify sớm nhất.
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest req = exchange.getRequest();
    String path = req.getPath().value();
    HttpMethod method = req.getMethod();

    // 1. Strip mọi header tin cậy do client tự gửi.
    ServerHttpRequest mutated =
        req.mutate()
            .headers(h -> TRUSTED_HEADERS.forEach(h::remove))
            .build();

    // 2. Parse Bearer token (có thể vắng mặt trên public endpoint).
    String authHeader = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    VerifiedClaims claims = null;
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7).trim();
      try {
        claims = verifier.verify(token);
      } catch (TokenExpiredException e) {
        return reject(exchange, HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_EXPIRED", "Token đã hết hạn");
      } catch (TokenInvalidException e) {
        return reject(
            exchange, HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "Token không hợp lệ");
      }
    }

    boolean publicEndpoint = isPublic(method, path);
    boolean adminEndpoint = isAdmin(path);

    // 3. Endpoint protected bắt buộc có token hợp lệ.
    if (!publicEndpoint && claims == null) {
      return reject(
          exchange, HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_MISSING", "Yêu cầu xác thực");
    }

    // 4. Endpoint admin yêu cầu role ADMIN.
    if (adminEndpoint && (claims == null || !claims.roles().contains("ADMIN"))) {
      return reject(
          exchange,
          HttpStatus.FORBIDDEN,
          "AUTH_ROLE_DENIED",
          "Tài khoản không có quyền ADMIN cho tài nguyên này");
    }

    // 5. Inject header tin cậy từ claims đã verify.
    if (claims != null) {
      VerifiedClaims c = claims;
      mutated =
          mutated
              .mutate()
              .header("X-User-Id", c.userId())
              .header("X-User-Roles", String.join(",", c.roles()))
              .header("X-Username", c.username() != null ? c.username() : "")
              .build();
    }

    return chain.filter(exchange.mutate().request(mutated).build());
  }

  private boolean isPublic(HttpMethod method, String path) {
    for (AuthProperties.PublicEndpoint ep : props.getPublicEndpoints()) {
      if (ep.method().equals(method) && matcher.match(ep.pattern(), path)) {
        return true;
      }
    }
    return false;
  }

  private boolean isAdmin(String path) {
    for (String pattern : props.getAdminPathPatterns()) {
      if (matcher.match(pattern, path)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Ghi response lỗi auth theo contract {@link ApiErrorResponse} chuẩn của gateway.
   * Reuse pattern từ {@code GlobalGatewayErrorHandler}.
   */
  private Mono<Void> reject(
      ServerWebExchange exchange, HttpStatus status, String code, String message) {
    ServerHttpResponse response = exchange.getResponse();
    String traceId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
    String path = exchange.getRequest().getURI().getPath();

    ApiErrorResponse body =
        ApiErrorResponse.of(
            status.value(),
            status.getReasonPhrase(),
            message,
            code,
            path,
            traceId,
            List.of());

    byte[] json;
    try {
      json = objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException e) {
      json = "{\"message\":\"Auth error\"}".getBytes(StandardCharsets.UTF_8);
    }

    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(json)));
  }
}
