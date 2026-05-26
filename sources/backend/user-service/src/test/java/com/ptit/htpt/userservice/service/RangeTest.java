package com.ptit.htpt.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 19 / Plan 19-02 Task 1 — pure JUnit unit tests cho {@link Range} (user-svc copy).
 */
class RangeTest {

  @Test
  void parse_validValues_returnsExpectedEnum() {
    assertThat(Range.parse("7d")).isEqualTo(Range.D7);
    assertThat(Range.parse("30d")).isEqualTo(Range.D30);
    assertThat(Range.parse("90d")).isEqualTo(Range.D90);
    assertThat(Range.parse("all")).isEqualTo(Range.ALL);
  }

  @Test
  void parse_null_returnsDefaultD30() {
    assertThat(Range.parse(null)).isEqualTo(Range.D30);
  }

  @Test
  void parse_invalid_throws400() {
    assertThatThrownBy(() -> Range.parse("invalid"))
        .isInstanceOf(ResponseStatusException.class)
        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
  }

  @Test
  void toFromInstant_D7_isApprox7DaysAgo() {
    Instant now = Instant.now();
    Instant from = Range.D7.toFromInstant();
    assertThat(from).isNotNull();
    long diffSec = Math.abs(ChronoUnit.SECONDS.between(now.minus(7, ChronoUnit.DAYS), from));
    assertThat(diffSec).isLessThan(5);
  }

  @Test
  void toFromInstant_ALL_returnsNull() {
    assertThat(Range.ALL.toFromInstant()).isNull();
  }
}
