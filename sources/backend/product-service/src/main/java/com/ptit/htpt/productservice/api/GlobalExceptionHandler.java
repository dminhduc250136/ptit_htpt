package com.ptit.htpt.productservice.api;

import com.ptit.htpt.productservice.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
            fe.getRejectedValue(),
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
}

