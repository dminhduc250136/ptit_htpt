package com.ptit.htpt.orderservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ptit.htpt.orderservice.exception.CouponErrorCode;
import com.ptit.htpt.orderservice.exception.CouponException;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * Phase 20 / Plan 20-02 Task 1 — Unit tests cho {@link CouponExceptionHandler}
 * (Test 1–4). Pure unit test với mocked HttpServletRequest.
 */
class CouponExceptionHandlerTest {

  private final CouponExceptionHandler handler = new CouponExceptionHandler();

  @Test
  void test1_errorCodeShape_8entries() {
    CouponErrorCode[] all = CouponErrorCode.values();
    assertThat(all).hasSize(8);

    assertThat(CouponErrorCode.COUPON_NOT_FOUND.httpStatus).isEqualTo(404);
    assertThat(CouponErrorCode.COUPON_INACTIVE.httpStatus).isEqualTo(422);
    assertThat(CouponErrorCode.COUPON_EXPIRED.httpStatus).isEqualTo(422);
    assertThat(CouponErrorCode.COUPON_MIN_ORDER_NOT_MET.httpStatus).isEqualTo(422);
    assertThat(CouponErrorCode.COUPON_ALREADY_REDEEMED.httpStatus).isEqualTo(409);
    assertThat(CouponErrorCode.COUPON_MAX_USES_REACHED.httpStatus).isEqualTo(409);
    assertThat(CouponErrorCode.COUPON_CONFLICT_OR_EXHAUSTED.httpStatus).isEqualTo(409);
    assertThat(CouponErrorCode.COUPON_HAS_REDEMPTIONS.httpStatus).isEqualTo(409);

    for (CouponErrorCode ec : all) {
      assertThat(ec.defaultMessageVi).isNotBlank();
    }
  }

  @Test
  void test2_couponException_carriesDetailsMap() {
    var ex = new CouponException(CouponErrorCode.COUPON_MIN_ORDER_NOT_MET,
        Map.of("minOrderAmount", BigDecimal.valueOf(100000)));

    assertThat(ex.errorCode()).isEqualTo(CouponErrorCode.COUPON_MIN_ORDER_NOT_MET);
    assertThat(ex.details()).containsEntry("minOrderAmount", BigDecimal.valueOf(100000));
  }

  @Test
  void test3_handler_returns422_forExpired() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/orders/coupons/validate");

    ResponseEntity<Map<String, Object>> resp = handler.handle(
        new CouponException(CouponErrorCode.COUPON_EXPIRED), req);

    assertThat(resp.getStatusCode().value()).isEqualTo(422);
    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody().get("code")).isEqualTo("COUPON_EXPIRED");
    assertThat(resp.getBody().get("message")).isEqualTo("Mã giảm giá đã hết hạn");
    assertThat(resp.getBody().get("path")).isEqualTo("/orders/coupons/validate");
    assertThat(resp.getBody()).containsKey("traceId");
  }

  @Test
  void test4_handler_includesDetailsMap() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/orders/coupons/validate");

    ResponseEntity<Map<String, Object>> resp = handler.handle(
        new CouponException(CouponErrorCode.COUPON_MIN_ORDER_NOT_MET,
            Map.of("minOrderAmount", BigDecimal.valueOf(100000))),
        req);

    assertThat(resp.getStatusCode().value()).isEqualTo(422);
    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody().get("code")).isEqualTo("COUPON_MIN_ORDER_NOT_MET");

    @SuppressWarnings("unchecked")
    Map<String, Object> details = (Map<String, Object>) resp.getBody().get("details");
    assertThat(details).containsEntry("minOrderAmount", BigDecimal.valueOf(100000));
  }

  @Test
  void test_handler_omitsDetailsWhenEmpty() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/orders/coupons/validate");

    ResponseEntity<Map<String, Object>> resp = handler.handle(
        new CouponException(CouponErrorCode.COUPON_NOT_FOUND), req);

    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody()).doesNotContainKey("details");
    assertThat(resp.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void test_handler_409_forAlreadyRedeemed() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/orders");

    ResponseEntity<Map<String, Object>> resp = handler.handle(
        new CouponException(CouponErrorCode.COUPON_ALREADY_REDEEMED), req);

    assertThat(resp.getStatusCode().value()).isEqualTo(409);
    assertThat(resp.getBody().get("code")).isEqualTo("COUPON_ALREADY_REDEEMED");
  }
}
