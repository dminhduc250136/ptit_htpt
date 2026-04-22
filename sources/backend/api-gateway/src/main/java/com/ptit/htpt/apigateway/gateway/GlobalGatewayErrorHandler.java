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

    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    String code = "INTERNAL_ERROR";
    String error = status.getReasonPhrase();
    String message = "Internal error";

    if (ex instanceof ResponseStatusException rse) {
      status = HttpStatus.valueOf(rse.getStatusCode().value());
      error = status.getReasonPhrase();
      message = rse.getReason() != null ? rse.getReason() : error;
      if (status == HttpStatus.NOT_FOUND) {
        code = "NOT_FOUND";
      } else if (status == HttpStatus.BAD_REQUEST) {
        code = "BAD_REQUEST";
      }
    } else if (ex != null && ex.getClass().getSimpleName().contains("NotFoundException")) {
      // Spring Cloud Gateway uses NotFoundException for missing routes.
      status = HttpStatus.NOT_FOUND;
      error = status.getReasonPhrase();
      message = "Not Found";
      code = "NOT_FOUND";
    }

    String traceId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
    String path = exchange.getRequest().getURI().getPath();

    ApiErrorResponse body =
        ApiErrorResponse.of(status.value(), error, message, code, path, traceId, List.of());

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
}

