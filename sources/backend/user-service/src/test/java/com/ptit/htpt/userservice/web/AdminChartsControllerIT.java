package com.ptit.htpt.userservice.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.userservice.domain.UserEntity;
import com.ptit.htpt.userservice.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
 * Phase 19 / Plan 19-02 Task 2 — integration tests cho /admin/users/charts/signups.
 *
 * <p>Cover: auth gate (401/403), invalid range (400), gap-fill, default range, range=all.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminChartsControllerIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("tmdt")
      .withUsername("tmdt")
      .withPassword("tmdt");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) throws Exception {
    postgres.start();
    try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var st = conn.createStatement()) {
      st.execute("CREATE SCHEMA IF NOT EXISTS user_svc");
    }
    registry.add("spring.datasource.url",
        () -> postgres.getJdbcUrl() + "?currentSchema=user_svc");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @LocalServerPort int port;
  @Autowired TestRestTemplate restTemplate;
  @Autowired UserRepository userRepo;
  @Value("${app.jwt.secret}") String jwtSecret;

  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    adminToken = makeToken("admin-1", "ADMIN");
    userToken = makeToken("user-1", "USER");
    // Clean slate for each test
    userRepo.deleteAll();
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
    return h;
  }

  private UserEntity newUserAt(String username, String email, Instant createdAt) {
    UserEntity u = UserEntity.create(username, email,
        "$2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu", "USER");
    try {
      Field f = UserEntity.class.getDeclaredField("createdAt");
      f.setAccessible(true);
      f.set(u, createdAt);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return u;
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  // --- Test 1: signups admin range=7d returns gap-filled list (8 entries) ---
  @Test
  void signups_admin_range7d_returnsGapFilledList() {
    Instant now = Instant.now();
    userRepo.saveAndFlush(newUserAt("u1", "u1@x", now.minus(2, ChronoUnit.DAYS)));
    userRepo.saveAndFlush(newUserAt("u2", "u2@x", now));

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/users/charts/signups?range=7d"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");
    assertThat(data).isNotNull();
    // 7d ago -> today inclusive = 8 entries
    assertThat(data.size()).isEqualTo(8);
    assertThat(data.get(0)).containsKeys("date", "count");
  }

  // --- Test 2: range=all returns from earliest signup date ---
  @Test
  void signups_admin_rangeAll_returnsList() {
    Instant now = Instant.now();
    userRepo.saveAndFlush(newUserAt("old", "old@x", now.minus(30, ChronoUnit.DAYS)));
    userRepo.saveAndFlush(newUserAt("new", "new@x", now));

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/users/charts/signups?range=all"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");
    assertThat(data).isNotEmpty();
  }

  // --- Test 3: no Bearer -> 401 ---
  @Test
  void signups_noBearer_returns401() {
    ResponseEntity<String> resp = restTemplate.exchange(
        url("/admin/users/charts/signups"),
        HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // --- Test 4: USER role -> 403 ---
  @Test
  void signups_userRole_returns403() {
    ResponseEntity<String> resp = restTemplate.exchange(
        url("/admin/users/charts/signups"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  // --- Test 5: invalid range -> 400 ---
  @Test
  void signups_invalidRange_returns400() {
    ResponseEntity<String> resp = restTemplate.exchange(
        url("/admin/users/charts/signups?range=invalid"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // --- Test 6: default range (no query param) -> 200 with 30d span (31 entries) ---
  @Test
  void signups_defaultRange_returns31Entries() {
    Instant now = Instant.now();
    userRepo.saveAndFlush(newUserAt("u1", "u1@x", now));

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/users/charts/signups"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");
    // 30d ago -> today inclusive = 31 entries
    assertThat(data.size()).isEqualTo(31);
  }
}
