package com.ptit.htpt.orderservice.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.orderservice.domain.CouponEntity;
import com.ptit.htpt.orderservice.domain.CouponRedemptionEntity;
import com.ptit.htpt.orderservice.domain.CouponType;
import com.ptit.htpt.orderservice.repository.CouponRedemptionRepository;
import com.ptit.htpt.orderservice.repository.CouponRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 20 / Plan 20-02 Task 2 — Integration tests cho 5 endpoints
 * {@link AdminCouponController} (D-14 list/get/create/update/patch/delete).
 *
 * <p>Cover Test C1–C11: auth (no Bearer / USER role), validation (invalid code),
 * happy CRUD, list filter active, get/update/delete not-found, PATCH active toggle,
 * DELETE redemption guard.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCouponControllerIT {

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
  @Value("${app.jwt.secret}") String jwtSecret;

  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    adminToken = makeToken("admin-1", "ADMIN");
    userToken = makeToken("user-1", "USER");
    redemptionRepo.deleteAll();
    couponRepo.deleteAll();
  }

  private String makeToken(String userId, String role) {
    SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    return Jwts.builder()
        .subject(userId)
        .claim("roles", role)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 3600_000L))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  private HttpHeaders authHeaders(String token) {
    HttpHeaders h = new HttpHeaders();
    if (token != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    return h;
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private CouponEntity persistCoupon(String code, boolean active, int initialUsedCount) {
    CouponEntity c = CouponEntity.create(code, CouponType.PERCENT,
        new BigDecimal("10"), BigDecimal.ZERO, null, null, active);
    if (initialUsedCount > 0) {
      try {
        var f = CouponEntity.class.getDeclaredField("usedCount");
        f.setAccessible(true);
        f.setInt(c, initialUsedCount);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return couponRepo.saveAndFlush(c);
  }

  // ---------- Test C1: POST happy admin → 201 ----------
  @Test
  void c1_postCreate_admin_returns201() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", "SUMMER2026");
    body.put("type", "PERCENT");
    body.put("value", 10);
    body.put("minOrderAmount", 0);
    body.put("active", true);

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/coupons"), HttpMethod.POST,
        new HttpEntity<>(body, authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
    assertThat(data.get("code")).isEqualTo("SUMMER2026");
    assertThat(data.get("type")).isEqualTo("PERCENT");
    assertThat(data.get("usedCount")).isEqualTo(0);
    assertThat(data.get("active")).isEqualTo(true);
    assertThat(data.get("id")).isNotNull();
  }

  // ---------- Test C2: POST no Bearer → 401 ----------
  @Test
  void c2_postCreate_noBearer_returns401() {
    Map<String, Object> body = Map.of(
        "code", "X1", "type", "PERCENT", "value", 10, "minOrderAmount", 0);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/coupons"), HttpMethod.POST,
        new HttpEntity<>(body, headers), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ---------- Test C3: POST USER role → 403 ----------
  @Test
  void c3_postCreate_userRole_returns403() {
    Map<String, Object> body = Map.of(
        "code", "X2", "type", "PERCENT", "value", 10, "minOrderAmount", 0);

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/coupons"), HttpMethod.POST,
        new HttpEntity<>(body, authHeaders(userToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ---------- Test C4: POST invalid code regex → 400 ----------
  @Test
  void c4_postCreate_invalidCode_returns400() {
    Map<String, Object> body = Map.of(
        "code", "abc", "type", "PERCENT", "value", 10, "minOrderAmount", 0);

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/coupons"), HttpMethod.POST,
        new HttpEntity<>(body, authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
  }

  // ---------- Test C5: GET list filter active ----------
  @Test
  void c5_listFilterActive() {
    persistCoupon("ACT1", true, 0);
    persistCoupon("ACT2", true, 0);
    persistCoupon("ACT3", true, 0);
    persistCoupon("INA1", false, 0);
    persistCoupon("INA2", false, 0);

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/coupons?active=true&size=50"), HttpMethod.GET,
        new HttpEntity<>(authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
    @SuppressWarnings("unchecked")
    var content = (java.util.List<Map<String, Object>>) data.get("content");
    assertThat(content).hasSize(3);
    assertThat(content).allMatch(m -> Boolean.TRUE.equals(m.get("active")));
  }

  // ---------- Test C6: GET detail happy ----------
  @Test
  void c6_getDetail_happy() {
    CouponEntity c = persistCoupon("DETAIL1", true, 0);

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/coupons/" + c.id()), HttpMethod.GET,
        new HttpEntity<>(authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
    assertThat(data.get("code")).isEqualTo("DETAIL1");
  }

  // ---------- Test C7: GET detail not found → 404 ----------
  @Test
  void c7_getDetail_notFound_returns404() {
    String fakeId = UUID.randomUUID().toString();

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/coupons/" + fakeId), HttpMethod.GET,
        new HttpEntity<>(authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody().get("code")).isEqualTo("COUPON_NOT_FOUND");
  }

  // ---------- Test C8: PUT update value ----------
  @Test
  void c8_putUpdate_changesValue() {
    CouponEntity c = persistCoupon("UPD1", true, 0);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", "UPD1");
    body.put("type", "PERCENT");
    body.put("value", 25);
    body.put("minOrderAmount", 0);
    body.put("active", true);

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/coupons/" + c.id()), HttpMethod.PUT,
        new HttpEntity<>(body, authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
    assertThat(new BigDecimal(data.get("value").toString())).isEqualByComparingTo("25");
  }

  // ---------- Test C9: PATCH /active toggle ----------
  @Test
  void c9_patchActive_toggle() {
    CouponEntity c = persistCoupon("TOG1", true, 0);

    Map<String, Object> body = Map.of("active", false);

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/coupons/" + c.id() + "/active"), HttpMethod.PATCH,
        new HttpEntity<>(body, authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    CouponEntity reloaded = couponRepo.findById(c.id()).orElseThrow();
    assertThat(reloaded.active()).isFalse();
  }

  // ---------- Test C10: DELETE no redemption → 204 ----------
  @Test
  void c10_delete_noRedemption_returns204() {
    CouponEntity c = persistCoupon("DEL-OK", true, 0);

    ResponseEntity<Void> resp = restTemplate.exchange(
        url("/admin/coupons/" + c.id()), HttpMethod.DELETE,
        new HttpEntity<>(authHeaders(adminToken)), Void.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(couponRepo.findById(c.id())).isEmpty();
  }

  // ---------- Test C11: DELETE has redemption → 409 COUPON_HAS_REDEMPTIONS ----------
  @Test
  void c11_delete_hasRedemption_returns409() {
    CouponEntity c = persistCoupon("DEL-BLOCKED", true, 0);
    redemptionRepo.saveAndFlush(
        CouponRedemptionEntity.create(c, "user-1", "order-1"));

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/coupons/" + c.id()), HttpMethod.DELETE,
        new HttpEntity<>(authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(resp.getBody().get("code")).isEqualTo("COUPON_HAS_REDEMPTIONS");
    assertThat(couponRepo.findById(c.id())).isPresent();
  }
}
