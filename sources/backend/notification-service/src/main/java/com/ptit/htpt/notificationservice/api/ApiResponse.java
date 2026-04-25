package com.ptit.htpt.notificationservice.api;

import java.time.Instant;

public record ApiResponse<T>(
    Instant timestamp,
    int status,
    String message,
    T data
) {
  public static <T> ApiResponse<T> of(int status, String message, T data) {
    return new ApiResponse<>(Instant.now(), status, message, data);
  }
}

