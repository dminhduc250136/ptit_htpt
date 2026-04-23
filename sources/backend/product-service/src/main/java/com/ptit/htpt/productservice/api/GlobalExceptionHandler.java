package com.ptit.htpt.productservice.api;

import com.ptit.htpt.productservice.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final int MAX_REJECTED_VALUE_LENGTH = 120;
  private static final String MASKED_VALUE = "***";
  private static final Set<String> SENSITIVE_FIELD_TOKENS = Set.of("password", "token", "secret");

  @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
  public org.springframework.http.ResponseEntity<ApiErrorResponse> handleValidation(
      Exception ex,
      HttpServletRequest request
  ) {
    List<FieldError> fieldErrors =
        ex instanceof MethodArgumentNotValidException manve
            ? manve.getBindingResult().getFieldErrors()
            : ((BindException) ex).getBindingResult().getFieldErrors();

    List<FieldErrorItem> items = fieldErrors.stream()
        .map(fe -> new FieldErrorItem(
            fe.getField(),
        sanitizeRejectedValue(fe.getField(), fe.getRejectedValue()),
            fe.getDefaultMessage()
        ))
        .toList();

    HttpStatus status = HttpStatus.BAD_REQUEST;
    return org.springframework.http.ResponseEntity.status(status).body(ApiErrorResponse.of(
        status.value(),
        status.getReasonPhrase(),
        "Validation failed",
        "VALIDATION_ERROR",
        request.getRequestURI(),
        getTraceId(request),
        items
    ));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public org.springframework.http.ResponseEntity<ApiErrorResponse> handleResponseStatus(
      ResponseStatusException ex,
      HttpServletRequest request
  ) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    String message = ex.getReason();
    if (message == null || message.isBlank()) {
      message = defaultMessage(status);
    }

    return org.springframework.http.ResponseEntity.status(status).body(ApiErrorResponse.of(
        status.value(),
        status.getReasonPhrase(),
        message,
        mapCommonCode(status),
        request.getRequestURI(),
        getTraceId(request),
        List.of()
    ));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public org.springframework.http.ResponseEntity<ApiErrorResponse> handleNotReadable(
      HttpMessageNotReadableException ex,
      HttpServletRequest request
  ) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    return org.springframework.http.ResponseEntity.status(status).body(ApiErrorResponse.of(
        status.value(),
        status.getReasonPhrase(),
        "Malformed JSON request",
        "BAD_REQUEST",
        request.getRequestURI(),
        getTraceId(request),
        List.of()
    ));
  }

  @ExceptionHandler(Exception.class)
  public org.springframework.http.ResponseEntity<ApiErrorResponse> handleFallback(
      Exception ex,
      HttpServletRequest request
  ) {
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    return org.springframework.http.ResponseEntity.status(status).body(ApiErrorResponse.of(
        status.value(),
        status.getReasonPhrase(),
        "Internal server error",
        "INTERNAL_ERROR",
        request.getRequestURI(),
        getTraceId(request),
        List.of()
    ));
  }

  private String getTraceId(HttpServletRequest request) {
    Object attr = request.getAttribute(TraceIdFilter.ATTR_NAME);
    if (attr instanceof String s && !s.isBlank()) {
      return s;
    }
    String header = request.getHeader(TraceIdFilter.HEADER_NAME);
    return header == null ? "" : header;
  }

  private Object sanitizeRejectedValue(String fieldName, Object rejectedValue) {
    if (rejectedValue == null) {
      return null;
    }
    if (isSensitiveField(fieldName)) {
      return MASKED_VALUE;
    }

    String text = String.valueOf(rejectedValue);
    if (text.length() <= MAX_REJECTED_VALUE_LENGTH) {
      return text;
    }
    return text.substring(0, MAX_REJECTED_VALUE_LENGTH) + "...";
  }

  private boolean isSensitiveField(String fieldName) {
    if (fieldName == null || fieldName.isBlank()) {
      return false;
    }
    String normalized = fieldName.toLowerCase(Locale.ROOT);
    return SENSITIVE_FIELD_TOKENS.stream().anyMatch(normalized::contains);
  }

  private String defaultMessage(HttpStatus status) {
    return switch (status) {
      case UNAUTHORIZED -> "Unauthorized";
      case FORBIDDEN -> "Forbidden";
      default -> status.getReasonPhrase();
    };
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

