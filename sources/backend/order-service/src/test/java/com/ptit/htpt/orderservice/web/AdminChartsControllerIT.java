package com.ptit.htpt.orderservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ptit.htpt.orderservice.domain.OrderEntity;
import com.ptit.htpt.orderservice.domain.OrderItemEntity;
import com.ptit.htpt.orderservice.repository.OrderRepository;
import com.ptit.htpt.orderservice.service.ProductBatchClient;
import com.ptit.htpt.orderservice.service.ProductBatchClient.ProductSummary;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 * Phase 19 / Plan 19-01 Task 2 — integration tests cho 3 admin chart endpoints.
 *
 * <p>Cover: auth gate (401/403), invalid range (400), revenue gap-fill, top-products
 * cross-svc enrichment + D-03 fallback, status distribution snapshot.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminChartsControllerIT {

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
  @Autowired OrderRepository orderRepo;
  @MockBean ProductBatchClient productBatchClient;
  @Value("${app.jwt.secret}") String jwtSecret;

  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    adminToken = makeToken("admin-1", "ADMIN");
    userToken = makeToken("user-1", "USER");
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

  private OrderEntity newOrder(String status, BigDecimal total, Instant createdAt) {
    OrderEntity o = OrderEntity.create("u-test", total, status, null);
    try {
      Field f = OrderEntity.class.getDeclaredField("createdAt");
      f.setAccessible(true);
      f.set(o, createdAt);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return o;
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  // --- Test 1: revenue endpoint with admin returns gap-filled list ---
  @Test
  void revenue_admin_range7d_returnsGapFilledList() {
    Instant now = Instant.now();
    orderRepo.saveAndFlush(newOrder("DELIVERED", new BigDecimal("100.00"),
        now.minus(2, ChronoUnit.DAYS)));
    orderRepo.saveAndFlush(newOrder("DELIVERED", new BigDecimal("200.00"), now));

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/orders/charts/revenue?range=7d"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");
    assertThat(data).isNotNull();
    // 7d ago -> today inclusive = 8 entries
    assertThat(data.size()).isEqualTo(8);
    // Each entry has date + value
    assertThat(data.get(0)).containsKeys("date", "value");
  }

  // --- Test 2: range=all returns from earliest delivered date ---
  @Test
  void revenue_admin_rangeAll_returnsList() {
    Instant now = Instant.now();
    orderRepo.saveAndFlush(newOrder("DELIVERED", new BigDecimal("50.00"), now));

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/orders/charts/revenue?range=all"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");
    assertThat(data).isNotEmpty();
  }

  // --- Test 3: no Bearer -> 401 ---
  @Test
  void revenue_noBearer_returns401() {
    ResponseEntity<String> resp = restTemplate.exchange(
        url("/admin/orders/charts/revenue"),
        HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // --- Test 4: USER role -> 403 ---
  @Test
  void revenue_userRole_returns403() {
    ResponseEntity<String> resp = restTemplate.exchange(
        url("/admin/orders/charts/revenue"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  // --- Test 5: invalid range -> 400 ---
  @Test
  void revenue_invalidRange_returns400() {
    ResponseEntity<String> resp = restTemplate.exchange(
        url("/admin/orders/charts/revenue?range=invalid"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // --- Test 6: top-products happy path with enrichment + auth forwarding ---
  @Test
  void topProducts_admin_enrichesViaProductBatchClient() {
    Instant now = Instant.now();
    for (int i = 0; i < 12; i++) {
      OrderEntity o = newOrder("DELIVERED", new BigDecimal("100.00"), now);
      OrderItemEntity item = OrderItemEntity.create(o, "p-" + i, "p" + i, 12 - i,
          new BigDecimal("100.00"));
      o.addItem(item);
      orderRepo.saveAndFlush(o);
    }
    Map<String, ProductSummary> enriched = new LinkedHashMap<>();
    for (int i = 0; i < 12; i++) {
      enriched.put("p-" + i, new ProductSummary("p-" + i, "Name " + i, "Brand " + i,
          "https://example.com/p" + i + ".webp"));
    }
    when(productBatchClient.fetchBatch(anyList(), any())).thenReturn(enriched);

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/orders/charts/top-products?range=30d"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");
    assertThat(data).hasSize(10);
    assertThat(data.get(0)).containsKeys("productId", "name", "brand", "thumbnailUrl", "qtySold");
    assertThat(data.get(0).get("name")).asString().startsWith("Name ");
    // D-03 auth forwarding sanity: ProductBatchClient nhận Bearer admin token
    verify(productBatchClient).fetchBatch(anyList(), eq("Bearer " + adminToken));
  }

  // --- Test 7: top-products fallback khi product-svc fail ---
  @Test
  void topProducts_admin_fallbackWhenEnrichmentEmpty() {
    Instant now = Instant.now();
    String fullId = "abcdef12-3456-7890-aaaa-bbbbccccdddd";
    OrderEntity o = newOrder("DELIVERED", new BigDecimal("100.00"), now);
    o.addItem(OrderItemEntity.create(o, fullId, "ignored", 5, new BigDecimal("100.00")));
    orderRepo.saveAndFlush(o);

    when(productBatchClient.fetchBatch(anyList(), any())).thenReturn(Map.of());

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/orders/charts/top-products?range=30d"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");
    assertThat(data).hasSize(1);
    Map<String, Object> entry = data.get(0);
    assertThat(entry.get("productId")).isEqualTo(fullId);
    assertThat(entry.get("name")).isEqualTo("Product " + fullId.substring(0, 8));
    assertThat(entry.get("brand")).isNull();
    assertThat(entry.get("thumbnailUrl")).isNull();
  }

  // --- Test 8: status-distribution returns counts per status ---
  @Test
  void statusDistribution_admin_returnsCountsPerStatus() {
    Instant now = Instant.now();
    orderRepo.saveAndFlush(newOrder("PENDING", new BigDecimal("10.00"), now));
    orderRepo.saveAndFlush(newOrder("PENDING", new BigDecimal("10.00"), now));
    orderRepo.saveAndFlush(newOrder("DELIVERED", new BigDecimal("10.00"), now));

    ResponseEntity<Map> resp = restTemplate.exchange(
        url("/admin/orders/charts/status-distribution"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), Map.class);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");
    assertThat(data).isNotEmpty();
    long pendingCount = data.stream()
        .filter(e -> "PENDING".equals(e.get("status")))
        .map(e -> ((Number) e.get("count")).longValue())
        .findFirst().orElse(0L);
    assertThat(pendingCount).isEqualTo(2L);
  }
}
