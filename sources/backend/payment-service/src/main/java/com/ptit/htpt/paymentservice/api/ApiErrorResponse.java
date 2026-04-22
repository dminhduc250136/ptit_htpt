package com.ptit.htpt.paymentservice.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String code,
    String path,
    String traceId,
    List<FieldErrorItem> fieldErrors
) {
  public static ApiErrorResponse of(
      int status,
      String error,
      String message,
      String code,
      String path,
      String traceId,
      List<FieldErrorItem> fieldErrors
  ) {
    return new ApiErrorResponse(
        Instant.now(),
        status,
        error,
        message,
        code,
        path,
        traceId,
        fieldErrors == null ? List.of() : fieldErrors
    );
  }
}

