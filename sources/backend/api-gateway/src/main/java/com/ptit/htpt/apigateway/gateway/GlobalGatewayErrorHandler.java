package com.ptit.htpt.apigateway.gateway;

import static com.ptit.htpt.apigateway.gateway.RequestIdFilter.REQUEST_ID_HEADER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GlobalGatewayErrorHandler implements ErrorWebExceptionHandler {
  private final ObjectMapper objectMapper;

  public GlobalGatewayErrorHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.error(ex);
    }

    String traceId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
    String path = exchange.getRequest().getURI().getPath();

    ApiErrorResponse passThrough = tryExtractPassThroughBody(ex, path, traceId);

    HttpStatus status;
    ApiErrorResponse body;
    if (passThrough != null) {
      status = resolveStatus(passThrough.status());
      body = passThrough;
    } else {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
      String code = "INTERNAL_ERROR";
      String error = status.getReasonPhrase();
      String message = "Internal error";

      if (ex instanceof ResponseStatusException rse) {
        status = resolveStatus(rse.getStatusCode().value());
        error = status.getReasonPhrase();
        message = rse.getReason() != null && !rse.getReason().isBlank() ? rse.getReason() : error;
        code = mapCommonCode(status);
      } else if (ex != null && ex.getClass().getSimpleName().contains("NotFoundException")) {
        // Spring Cloud Gateway uses NotFoundException for missing routes.
        status = HttpStatus.NOT_FOUND;
        error = status.getReasonPhrase();
        message = "Not Found";
        code = "NOT_FOUND";
      }

      body = ApiErrorResponse.of(
          status.value(),
          error,
          message,
          code,
          path,
          traceId,
          List.of()
      );
    }

    byte[] json;
    try {
      json = objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException jsonEx) {
      json = "{\"message\":\"Internal error\"}".getBytes(StandardCharsets.UTF_8);
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(json)));
  }

  private ApiErrorResponse tryExtractPassThroughBody(Throwable ex, String path, String traceId) {
    if (!(ex instanceof ResponseStatusException rse)) {
      return null;
    }
    String reason = rse.getReason();
    if (reason == null || reason.isBlank() || !reason.trim().startsWith("{")) {
      return null;
    }

    try {
      ApiErrorResponse parsed = objectMapper.readValue(reason, ApiErrorResponse.class);
      if (!isCompliant(parsed)) {
        return null;
      }

      HttpStatus parsedStatus = resolveStatus(parsed.status());
      String resolvedError = hasText(parsed.error()) ? parsed.error() : parsedStatus.getReasonPhrase();
      String resolvedMessage = hasText(parsed.message()) ? parsed.message() : parsedStatus.getReasonPhrase();
      String resolvedCode = hasText(parsed.code()) ? parsed.code() : mapCommonCode(parsedStatus);
      String resolvedPath = hasText(parsed.path()) ? parsed.path() : path;
      String resolvedTraceId = hasText(parsed.traceId()) ? parsed.traceId() : traceId;

      return ApiErrorResponse.of(
          parsedStatus.value(),
          resolvedError,
          resolvedMessage,
          resolvedCode,
          resolvedPath,
          resolvedTraceId,
          parsed.fieldErrors()
      );
    } catch (JsonProcessingException ignored) {
      return null;
    }
  }

  private boolean isCompliant(ApiErrorResponse payload) {
    return payload != null
        && payload.status() > 0
        && hasText(payload.code())
        && hasText(payload.message());
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private HttpStatus resolveStatus(int statusCode) {
    HttpStatus status = HttpStatus.resolve(statusCode);
    return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
  }

  private String mapCommonCode(HttpStatus status) {
    return switch (status) {
      case BAD_REQUEST -> "BAD_REQUEST";
      case NOT_FOUND -> "NOT_FOUND";
      case CONFLICT -> "CONFLICT";
      case UNAUTHORIZED -> "UNAUTHORIZED";
      case FORBIDDEN -> "FORBIDDEN";
      default -> status.is4xxClientError() ? "BAD_REQUEST" : "INTERNAL_ERROR";
    };
  }
}

