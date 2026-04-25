package com.ptit.htpt.apigateway.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

class GlobalGatewayErrorHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private GlobalGatewayErrorHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalGatewayErrorHandler(objectMapper);
  }

  @Test
  void handle_returnsNotFoundEnvelope_forSpringCloudGatewayMissingRoute() {
    MockServerWebExchange exchange = newExchange("/api/does-not-exist/foo", "trace-nf");

    handler
        .handle(exchange, new org.springframework.cloud.gateway.support.NotFoundException("No route"))
        .block();

    ApiErrorResponse body = readBody(exchange);
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(body.status()).isEqualTo(404);
    assertThat(body.code()).isEqualTo("NOT_FOUND");
    assertThat(body.path()).isEqualTo("/api/does-not-exist/foo");
    assertThat(body.traceId()).isEqualTo("trace-nf");
    assertThat(body.fieldErrors()).isEmpty();
    assertThat(body.timestamp()).isNotNull();
  }

  @Test
  void handle_mapsUnauthorized_fromResponseStatusException() {
    MockServerWebExchange exchange = newExchange("/api/users/me", "trace-401");

    handler
        .handle(exchange, new ResponseStatusException(HttpStatus.UNAUTHORIZED, "token expired"))
        .block();

    ApiErrorResponse body = readBody(exchange);
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(body.status()).isEqualTo(401);
    assertThat(body.code()).isEqualTo("UNAUTHORIZED");
    assertThat(body.message()).isEqualTo("token expired");
  }

  @Test
  void handle_mapsForbidden_fromResponseStatusException() {
    MockServerWebExchange exchange = newExchange("/api/users/1", "trace-403");

    handler.handle(exchange, new ResponseStatusException(HttpStatus.FORBIDDEN, null)).block();

    ApiErrorResponse body = readBody(exchange);
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(body.code()).isEqualTo("FORBIDDEN");
    assertThat(body.message()).isEqualTo("Forbidden");
  }

  @Test
  void handle_fallsBackToInternalError_forGenericThrowable() {
    MockServerWebExchange exchange = newExchange("/api/orders", "trace-500");

    handler.handle(exchange, new RuntimeException("kaboom")).block();

    ApiErrorResponse body = readBody(exchange);
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(body.status()).isEqualTo(500);
    assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
    assertThat(body.message()).isEqualTo("Internal error");
    assertThat(body.traceId()).isEqualTo("trace-500");
  }

  @Test
  void handle_passesThroughCompliantDownstreamErrorBody() throws Exception {
    ApiErrorResponse downstream = ApiErrorResponse.of(
        400,
        "Bad Request",
        "Validation failed",
        "VALIDATION_ERROR",
        "/users/register",
        "downstream-trace",
        List.of(new FieldErrorItem("name", "", "must not be blank")));
    String downstreamJson = objectMapper.writeValueAsString(downstream);

    MockServerWebExchange exchange = newExchange("/api/users/register", "gateway-trace");
    handler
        .handle(exchange, new ResponseStatusException(HttpStatus.BAD_REQUEST, downstreamJson))
        .block();

    ApiErrorResponse body = readBody(exchange);
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body.status()).isEqualTo(400);
    assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
    assertThat(body.message()).isEqualTo("Validation failed");
    assertThat(body.path()).isEqualTo("/users/register");
    assertThat(body.traceId()).isEqualTo("downstream-trace");
    assertThat(body.fieldErrors()).hasSize(1);
    assertThat(body.fieldErrors().get(0).field()).isEqualTo("name");
    assertThat(body.fieldErrors().get(0).message()).isEqualTo("must not be blank");
  }

  @Test
  void handle_writesJsonContentTypeOnResponse() {
    MockServerWebExchange exchange = newExchange("/api/products", "trace-ct");

    handler.handle(exchange, new RuntimeException("boom")).block();

    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);
  }

  private ApiErrorResponse readBody(MockServerWebExchange exchange) {
    MockServerHttpResponse response = exchange.getResponse();
    String json = response.getBodyAsString().block();
    if (json == null || json.isBlank()) {
      throw new AssertionError("no response body written");
    }
    try {
      return objectMapper.readValue(json, ApiErrorResponse.class);
    } catch (Exception e) {
      throw new AssertionError("failed to parse response body: " + json, e);
    }
  }

  private static MockServerWebExchange newExchange(String path, String traceId) {
    return MockServerWebExchange.from(
        MockServerHttpRequest.get(path)
            .header(RequestIdFilter.REQUEST_ID_HEADER, traceId)
            .build());
  }
}
