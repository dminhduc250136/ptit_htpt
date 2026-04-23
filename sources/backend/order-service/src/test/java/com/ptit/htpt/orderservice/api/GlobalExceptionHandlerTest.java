package com.ptit.htpt.orderservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.orderservice.web.TraceIdFilter;
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
    br.addError(new FieldError("target", "customerName", null, false, null, null, "must not be blank"));
    BindException ex = new BindException(br);

    ResponseEntity<ApiErrorResponse> response =
        handler.handleValidation(ex, newRequest("/api/orders", "order-trace-1"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    ApiErrorResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo(400);
    assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
    assertThat(body.message()).isEqualTo("Validation failed");
    assertThat(body.path()).isEqualTo("/api/orders");
    assertThat(body.traceId()).isEqualTo("order-trace-1");
    assertThat(body.fieldErrors()).hasSize(1);
    assertThat(body.fieldErrors().get(0).field()).isEqualTo("customerName");
  }

  @Test
  void handleValidation_masksSensitiveFields() throws Exception {
    Target target = new Target();
    BeanPropertyBindingResult br = new BeanPropertyBindingResult(target, "target");
    br.addError(new FieldError("target", "paymentSecret", "raw-secret", false, null, null, "must not be blank"));
    BindException ex = new BindException(br);

    ResponseEntity<ApiErrorResponse> response =
        handler.handleValidation(ex, newRequest("/api/orders", "t-mask"));

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().fieldErrors().get(0).rejectedValue()).isEqualTo("***");
  }

  @Test
  void handleValidation_truncatesLongNonSensitiveValues() throws Exception {
    String longValue = "a".repeat(200);
    Target target = new Target();
    BeanPropertyBindingResult br = new BeanPropertyBindingResult(target, "target");
    br.addError(new FieldError("target", "note", longValue, false, null, null, "size must be between 1 and 60"));
    BindException ex = new BindException(br);

    ResponseEntity<ApiErrorResponse> response =
        handler.handleValidation(ex, newRequest("/api/orders", "t-trunc"));

    assertThat(response.getBody()).isNotNull();
    String truncated = (String) response.getBody().fieldErrors().get(0).rejectedValue();
    assertThat(truncated).endsWith("...");
    assertThat(truncated).hasSize(120 + 3);
  }

  @Test
  void handleNotReadable_returnsBadRequestCode() {
    HttpMessageNotReadableException ex =
        new HttpMessageNotReadableException("malformed", new MockHttpInputMessage(new byte[0]));

    ResponseEntity<ApiErrorResponse> response =
        handler.handleNotReadable(ex, newRequest("/api/orders", "t-nr"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
    assertThat(response.getBody().fieldErrors()).isEmpty();
  }

  @Test
  void handleFallback_returnsInternalErrorCode() {
    ResponseEntity<ApiErrorResponse> response =
        handler.handleFallback(new RuntimeException("boom"), newRequest("/api/orders", "t-fb"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
  }

  @Test
  void handleResponseStatus_mapsUnauthorizedAndForbidden() {
    ResponseEntity<ApiErrorResponse> unauthorized =
        handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "needs auth"),
            newRequest("/api/orders", "t-auth"));
    ResponseEntity<ApiErrorResponse> forbidden =
        handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.FORBIDDEN, ""),
            newRequest("/api/orders", "t-forb"));

    assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unauthorized.getBody()).isNotNull();
    assertThat(unauthorized.getBody().code()).isEqualTo("UNAUTHORIZED");

    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(forbidden.getBody()).isNotNull();
    assertThat(forbidden.getBody().code()).isEqualTo("FORBIDDEN");
    assertThat(forbidden.getBody().message()).isEqualTo("Forbidden");
  }

  private static MockHttpServletRequest newRequest(String uri, String traceId) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(uri);
    request.setAttribute(TraceIdFilter.ATTR_NAME, traceId);
    return request;
  }

  private static class Target {
    @SuppressWarnings("unused")
    private String customerName;

    @SuppressWarnings("unused")
    private String paymentSecret;

    @SuppressWarnings("unused")
    private String note;

    String getCustomerName() {
      return customerName;
    }

    void setCustomerName(String customerName) {
      this.customerName = customerName;
    }

    String getPaymentSecret() {
      return paymentSecret;
    }

    void setPaymentSecret(String paymentSecret) {
      this.paymentSecret = paymentSecret;
    }

    String getNote() {
      return note;
    }

    void setNote(String note) {
      this.note = note;
    }
  }
}
