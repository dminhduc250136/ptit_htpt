package com.ptit.htpt.orderservice.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.orderservice.domain.CouponType;
import com.ptit.htpt.orderservice.web.CouponDtos.CreateCouponRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Phase 20 / Plan 20-02 Task 1 — jakarta validation tests cho
 * {@link CreateCouponRequest} (Test 5–7). Test 8 (type enum binding) defer
 * cho controller IT vì Spring binder, không phải bean validation.
 */
class CouponDtosValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    if (factory != null) {
      factory.close();
    }
  }

  @Test
  void test5_createValid_passes() {
    var req = new CreateCouponRequest(
        "SUMMER2026",
        CouponType.PERCENT,
        BigDecimal.valueOf(10),
        BigDecimal.ZERO,
        null,
        null,
        Boolean.TRUE
    );

    Set<ConstraintViolation<CreateCouponRequest>> violations = validator.validate(req);

    assertThat(violations).isEmpty();
  }

  @Test
  void test6_createCodeRegexFail() {
    var req = new CreateCouponRequest(
        "abc",
        CouponType.PERCENT,
        BigDecimal.valueOf(10),
        BigDecimal.ZERO,
        null,
        null,
        Boolean.TRUE
    );

    Set<ConstraintViolation<CreateCouponRequest>> violations = validator.validate(req);

    assertThat(violations).isNotEmpty();
    assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("code")))
        .isTrue();
  }

  @Test
  void test7_createValueZeroFail() {
    var req = new CreateCouponRequest(
        "SUMMER2026",
        CouponType.PERCENT,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        null,
        Boolean.TRUE
    );

    Set<ConstraintViolation<CreateCouponRequest>> violations = validator.validate(req);

    assertThat(violations).isNotEmpty();
    assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("value")))
        .isTrue();
  }

  @Test
  void test_codeNullFail() {
    var req = new CreateCouponRequest(
        null,
        CouponType.PERCENT,
        BigDecimal.valueOf(10),
        BigDecimal.ZERO,
        null,
        null,
        Boolean.TRUE
    );

    Set<ConstraintViolation<CreateCouponRequest>> violations = validator.validate(req);

    assertThat(violations).isNotEmpty();
  }

  @Test
  void test_minOrderNegativeFail() {
    var req = new CreateCouponRequest(
        "SUMMER2026",
        CouponType.PERCENT,
        BigDecimal.valueOf(10),
        BigDecimal.valueOf(-1),
        null,
        null,
        Boolean.TRUE
    );

    Set<ConstraintViolation<CreateCouponRequest>> violations = validator.validate(req);

    assertThat(violations).isNotEmpty();
    assertThat(violations.stream()
        .anyMatch(v -> v.getPropertyPath().toString().equals("minOrderAmount")))
        .isTrue();
  }
}
