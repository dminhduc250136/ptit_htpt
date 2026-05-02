package com.ptit.htpt.orderservice.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.orderservice.domain.CouponEntity;
import com.ptit.htpt.orderservice.domain.CouponRedemptionEntity;
import com.ptit.htpt.orderservice.domain.CouponType;
import com.ptit.htpt.orderservice.repository.CouponRedemptionRepository;
import com.ptit.htpt.orderservice.repository.CouponRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 20 / Plan 20-03 Task 1 — IT cho CouponPreviewController POST /orders/coupons/validate.
 *
 * <p>Cover Test 3-7: happy logged-in, already-redeemed user, missing X-User-Id (anonymous),
 * validation (missing code), invalid coupon (NOT_FOUND).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponPreviewControllerIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("tmdt")
      .withUsername("tmdt")
      .withPassword("tmdt")
      .withInitScript("test-init/01-schemas.sql");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url",
        () -> postgres.getJdbcUrl() + "?currentSchema=order_svc");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @LocalServerPort int port;
  @Autowired TestRestTemplate restTemplate;
  @Autowired CouponRepository couponRepo;
  @Autowired CouponRedemptionRepository redemptionRepo;

  @BeforeEach
  void setUp() {
    redemptionRepo.deleteAll();
    couponRepo.deleteAll();
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private HttpHeaders jsonHeaders(String userId) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    if (userId != null) h.set("X-User-Id", userId);
    return h;
  }

  private CouponEntity persistPercentCoupon(String code, BigDecimal value, BigDecimal minOrder) {
    CouponEntity c = CouponEntity.create(code, CouponType.PERCENT, value, minOrder, null, null, true);
    return couponRepo.saveAndFlush(c);
  }

  // ---------- Test 3: happy logged-in ----------
  @Test
  void test3_happyLoggedIn_returnsPreview() {
    persistPercentCoupon("SALE10", new BigDecimal("10"), BigDecimal.ZERO);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", "SALE10");
    body.put("cartTotal", 1500000);

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/orders/coupons/validate"), HttpMethod.POST,
        new HttpEntity<>(body, jsonHeaders("u1")), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
    assertThat(data.get("code")).isEqualTo("SALE10");
    assertThat(new BigDecimal(data.get("discountAmount").toString())).isEqualByComparingTo("150000");
    assertThat(new BigDecimal(data.get("finalTotal").toString())).isEqualByComparingTo("1350000");
  }

  // ---------- Test 4: logged-in user already redeemed → 409 ----------
  @Test
  void test4_userAlreadyRedeemed_returns409() {
    CouponEntity c = persistPercentCoupon("ONCE", new BigDecimal("10"), BigDecimal.ZERO);
    redemptionRepo.saveAndFlush(CouponRedemptionEntity.create(c, "u1", "order-prev"));

    Map<String, Object> body = Map.of("code", "ONCE", "cartTotal", 500000);

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/orders/coupons/validate"), HttpMethod.POST,
        new HttpEntity<>(body, jsonHeaders("u1")), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(resp.getBody().get("code")).isEqualTo("COUPON_ALREADY_REDEEMED");
  }

  // ---------- Test 5: missing X-User-Id → skip user redemption check, valid coupon → 200 ----------
  @Test
  void test5_missingUserHeader_skipsRedemptionCheck() {
    CouponEntity c = persistPercentCoupon("ANON", new BigDecimal("5"), BigDecimal.ZERO);
    // Seed redemption cho 1 user khác — endpoint vô danh KHÔNG dùng userId nên redemption không match
    redemptionRepo.saveAndFlush(CouponRedemptionEntity.create(c, "other-user", "order-x"));

    Map<String, Object> body = Map.of("code", "ANON", "cartTotal", 200000);

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/orders/coupons/validate"), HttpMethod.POST,
        new HttpEntity<>(body, jsonHeaders(null)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
    assertThat(new BigDecimal(data.get("discountAmount").toString())).isEqualByComparingTo("10000");
    assertThat(new BigDecimal(data.get("finalTotal").toString())).isEqualByComparingTo("190000");
  }

  // ---------- Test 6: missing code → 400 VALIDATION_ERROR ----------
  @Test
  void test6_missingCode_returns400() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("cartTotal", 100000);
    // KHÔNG có code

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/orders/coupons/validate"), HttpMethod.POST,
        new HttpEntity<>(body, jsonHeaders("u1")), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
  }

  // ---------- Test 7: invalid coupon code → 404 COUPON_NOT_FOUND ----------
  @Test
  void test7_unknownCode_returns404() {
    Map<String, Object> body = Map.of("code", "DOESNOTEXIST", "cartTotal", 100000);

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/orders/coupons/validate"), HttpMethod.POST,
        new HttpEntity<>(body, jsonHeaders("u1")), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody().get("code")).isEqualTo("COUPON_NOT_FOUND");
  }
}
