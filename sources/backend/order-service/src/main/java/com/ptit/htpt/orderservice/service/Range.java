package com.ptit.htpt.orderservice.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 19 / Plan 19-01 (D-04): time-window enum cho admin chart endpoints.
 *
 * <p>Mapping:
 * <ul>
 *   <li>{@code 7d}  → {@link #D7}</li>
 *   <li>{@code 30d} → {@link #D30} (default khi {@code s == null})</li>
 *   <li>{@code 90d} → {@link #D90}</li>
 *   <li>{@code all} → {@link #ALL} (no filter, {@link #toFromInstant()} returns null)</li>
 * </ul>
 *
 * <p>Invalid input → {@link ResponseStatusException} 400 (D-04 explicit).
 */
public enum Range {
  D7(7), D30(30), D90(90), ALL(null);

  private final Integer days;

  Range(Integer days) {
    this.days = days;
  }

  public Instant toFromInstant() {
    return days == null ? null : Instant.now().minus(days, ChronoUnit.DAYS);
  }

  public static Range parse(String s) {
    return switch (s == null ? "30d" : s) {
      case "7d" -> D7;
      case "30d" -> D30;
      case "90d" -> D90;
      case "all" -> ALL;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Invalid range: " + s + " (expected 7d|30d|90d|all)");
    };
  }
}
