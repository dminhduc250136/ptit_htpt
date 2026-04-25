package com.ptit.htpt.userservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.userservice.web.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleValidation_returnsValidationErrorEnvelope() throws Exception {
    Target target = new Target();
    BeanPropertyBindingResult br = new BeanPropertyBindingResult(target, "target");
    br.addError(new FieldError("target", "name", null, false, null, null, "must not be blank"));
    BindException ex = new BindException(br);

    MockHttpServletRequest request = newRequest("/api/users/register", "trace-abc");

    ResponseEntity<ApiErrorResponse> response = handler.handleValidation(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    ApiErrorResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo(400);
    assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
    assertThat(body.error()).isEqualTo("Bad Request");
    assertThat(body.message()).isEqualTo("Validation failed");
    assertThat(body.path()).isEqualTo("/api/users/register");
    assertThat(body.traceId()).isEqualTo("trace-abc");
    assertThat(body.timestamp()).isNotNull();
    assertThat(body.fieldErrors()).hasSize(1);
    FieldErrorItem item = body.fieldErrors().get(0);
    assertThat(item.field()).isEqualTo("name");
    assertThat(item.message()).isEqualTo("must not be blank");
  }

  @Test
  void handleValidation_masksSensitiveFields() throws Exception {
    Target target = new Target();
    BeanPropertyBindingResult br = new BeanPropertyBindingResult(target, "target");
    br.addError(new FieldError("target", "password", "plain-secret", false, null, null, "size must be between 8 and 64"));
    br.addError(new FieldError("target", "apiToken", "leaked-token", false, null, null, "invalid token format"));
    BindException ex = new BindException(br);

    ResponseEntity<ApiErrorResponse> response =
        handler.handleValidation(ex, newRequest("/api/users", "t-1"));

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().fieldErrors())
        .extracting(FieldErrorItem::field, FieldErrorItem::rejectedValue)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("password", "***"),
            org.assertj.core.groups.Tuple.tuple("apiToken", "***"));
  }

  @Test
  void handleValidation_truncatesLongNonSensitiveValues() throws Exception {
    String longValue = "x".repeat(200);
    Target target = new Target();
    BeanPropertyBindingResult br = new BeanPropertyBindingResult(target, "target");
    br.addError(new FieldError("target", "name", longValue, false, null, null, "size must be between 1 and 60"));
    BindException ex = new BindException(br);

    ResponseEntity<ApiErrorResponse> response =
        handler.handleValidation(ex, newRequest("/api/users", "t-2"));

    assertThat(response.getBody()).isNotNull();
    String truncated = (String) response.getBody().fieldErrors().get(0).rejectedValue();
    assertThat(truncated).endsWith("...");
    assertThat(truncated).hasSize(120 + 3);
  }

  @Test
  void handleNotReadable_returnsBadRequestEnvelope() {
    HttpMessageNotReadableException ex =
        new HttpMessageNotReadableException("malformed", new MockHttpInputMessage(new byte[0]));

    ResponseEntity<ApiErrorResponse> response =
        handler.handleNotReadable(ex, newRequest("/api/users", "t-3"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    ApiErrorResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.code()).isEqualTo("BAD_REQUEST");
    assertThat(body.message()).isEqualTo("Malformed JSON request");
    assertThat(body.fieldErrors()).isEmpty();
    assertThat(body.traceId()).isEqualTo("t-3");
  }

  @Test
  void handleFallback_returnsInternalErrorEnvelope() {
    RuntimeException ex = new RuntimeException("boom");

    ResponseEntity<ApiErrorResponse> response =
        handler.handleFallback(ex, newRequest("/api/users", "t-4"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    ApiErrorResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
    assertThat(body.message()).isEqualTo("Internal server error");
    assertThat(body.status()).isEqualTo(500);
  }

  @Test
  void handleResponseStatus_mapsUnauthorized() {
    ResponseStatusException ex = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad credentials");

    ResponseEntity<ApiErrorResponse> response =
        handler.handleResponseStatus(ex, newRequest("/api/users", "t-5"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("UNAUTHORIZED");
    assertThat(response.getBody().message()).isEqualTo("bad credentials");
  }

  @Test
  void handleResponseStatus_mapsForbidden() {
    ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN, "");

    ResponseEntity<ApiErrorResponse> response =
        handler.handleResponseStatus(ex, newRequest("/api/users", "t-6"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
    assertThat(response.getBody().message()).isEqualTo("Forbidden");
  }

  @Test
  void getTraceId_fallsBackToRequestHeader_whenAttributeMissing() {
    RuntimeException ex = new RuntimeException("boom");
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRequestURI("/api/users");
    req.addHeader(TraceIdFilter.HEADER_NAME, "from-header");

    ResponseEntity<ApiErrorResponse> response = handler.handleFallback(ex, req);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().traceId()).isEqualTo("from-header");
  }

  private static MockHttpServletRequest newRequest(String uri, String traceId) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(uri);
    request.setAttribute(TraceIdFilter.ATTR_NAME, traceId);
    return request;
  }

  private static class Target {
    @SuppressWarnings("unused")
    private String name;

    @SuppressWarnings("unused")
    private String password;

    @SuppressWarnings("unused")
    private String apiToken;

    String getName() {
      return name;
    }

    void setName(String name) {
      this.name = name;
    }

    String getPassword() {
      return password;
    }

    void setPassword(String password) {
      this.password = password;
    }

    String getApiToken() {
      return apiToken;
    }

    void setApiToken(String apiToken) {
      this.apiToken = apiToken;
    }
  }
}
