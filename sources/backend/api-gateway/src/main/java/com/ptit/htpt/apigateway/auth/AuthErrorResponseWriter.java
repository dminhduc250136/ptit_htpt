package com.ptit.htpt.apigateway.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.htpt.apigateway.gateway.ApiErrorResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Phase 25 Plan 02: helper ghi response lỗi xác thực theo contract
 * {@link ApiErrorResponse} chuẩn của gateway.
 *
 * <p>Tách riêng khỏi {@code JwtAuthenticationFilter} để logic serialize +
 * ghi buffer được tái dùng và test độc lập.
 */
@Component
public class AuthErrorResponseWriter {

  private final ObjectMapper objectMapper;

  public AuthErrorResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Ghi response lỗi auth dạng JSON.
   *
   * @param exchange exchange hiện tại
   * @param status HTTP status (401 / 403)
   * @param code mã lỗi nghiệp vụ (AUTH_TOKEN_MISSING / AUTH_TOKEN_INVALID / ...)
   * @param message thông điệp tiếng Việt cho client
   */
  public Mono<Void> write(
      ServerWebExchange exchange, HttpStatus status, String code, String message) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.empty();
    }

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
