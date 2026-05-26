package com.ptit.htpt.productservice.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.htpt.productservice.domain.CategoryEntity;
import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.domain.ReviewEntity;
import com.ptit.htpt.productservice.repository.CategoryRepository;
import com.ptit.htpt.productservice.repository.ProductRepository;
import com.ptit.htpt.productservice.repository.ReviewRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 21 / Plan 02 — REV-06 admin moderation controller tests.
 *
 * URL pattern: /admin/products/reviews (Finding 1 — gateway rewrite
 * /api/products/admin/reviews/... → /admin/products/reviews/...).
 *
 * Auth coverage: missing header → 401, non-admin token → 403, admin token → 200/204.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminReviewControllerTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url",
        () -> postgres.getJdbcUrl() + "?currentSchema=product_svc");
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.schemas", () -> "product_svc");
    r.add("spring.flyway.default-schema", () -> "product_svc");
    r.add("spring.jpa.properties.hibernate.default_schema", () -> "product_svc");
  }

  @BeforeAll
  static void initSchema() throws Exception {
    postgres.start();
    try (Connection conn = postgres.createConnection("");
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE SCHEMA IF NOT EXISTS product_svc");
    }
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired ReviewRepository reviewRepo;
  @Autowired ProductRepository productRepo;
  @Autowired CategoryRepository categoryRepo;
  @MockBean RestTemplate restTemplate;
  @Value("${app.jwt.secret}") String jwtSecret;

  ProductEntity product;

  @BeforeEach
  void seedProduct() {
    reviewRepo.deleteAll();
    CategoryEntity cat = CategoryEntity.create("Cat-Adm", "cat-adm-" + System.nanoTime());
    categoryRepo.save(cat);
    product = ProductEntity.create(
        "P-Adm", "p-adm-" + System.nanoTime(),
        cat.id(), new BigDecimal("100000.00"), "ACTIVE",
        null, null, null, null);
    productRepo.save(product);
  }

  private String makeToken(String userId, String roles) {
    var key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    return Jwts.builder().subject(userId)
        .claim("username", userId)
        .claim("roles", roles)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 3600_000L))
        .signWith(key, Jwts.SIG.HS256).compact();
  }

  private String seedReview(String userId, int rating) {
    ReviewEntity r = ReviewEntity.create(product.id(), userId, "Reviewer-" + userId, rating, "content");
    reviewRepo.save(r);
    return r.id();
  }

  // -------------------------------------------------------------------------
  // GET /admin/products/reviews
  // -------------------------------------------------------------------------

  @Test
  void list_missingAuth_returns401() throws Exception {
    mockMvc.perform(get("/admin/products/reviews?filter=all"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void list_nonAdminToken_returns403() throws Exception {
    String token = makeToken("u-1", "USER");
    mockMvc.perform(get("/admin/products/reviews?filter=all")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void list_adminToken_returnsPaginatedWithSlugAndHidden() throws Exception {
    seedReview("u-l1", 5);
    seedReview("u-l2", 4);
    String token = makeToken("admin-1", "ADMIN");

    mockMvc.perform(get("/admin/products/reviews?filter=all&page=0&size=20")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].productSlug").exists())
        .andExpect(jsonPath("$.data.content[0].hidden").exists())
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  // -------------------------------------------------------------------------
  // PATCH /admin/products/reviews/{reviewId}/visibility
  // -------------------------------------------------------------------------

  @Test
  void setVisibility_admin_returns200AndPersistsHidden() throws Exception {
    String rid = seedReview("u-v1", 5);
    String token = makeToken("admin-2", "ADMIN");
    String body = objectMapper.writeValueAsString(Map.of("hidden", true));

    mockMvc.perform(patch("/admin/products/reviews/{rid}/visibility", rid)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hidden").value(true));

    ReviewEntity persisted = reviewRepo.findById(rid).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(persisted.hidden()).isTrue();
  }

  @Test
  void setVisibility_nonAdmin_returns403() throws Exception {
    String rid = seedReview("u-v2", 5);
    String token = makeToken("u-x", "USER");
    String body = objectMapper.writeValueAsString(Map.of("hidden", true));

    mockMvc.perform(patch("/admin/products/reviews/{rid}/visibility", rid)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden());
  }

  // -------------------------------------------------------------------------
  // DELETE /admin/products/reviews/{reviewId}
  // -------------------------------------------------------------------------

  @Test
  void hardDelete_admin_returns204AndRemovesRow() throws Exception {
    String rid = seedReview("u-hd", 5);
    String token = makeToken("admin-3", "ADMIN");

    mockMvc.perform(delete("/admin/products/reviews/{rid}", rid)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(reviewRepo.findById(rid)).isEmpty();
  }

  @Test
  void hardDelete_missingAuth_returns401() throws Exception {
    String rid = seedReview("u-hd-2", 5);
    mockMvc.perform(delete("/admin/products/reviews/{rid}", rid))
        .andExpect(status().isUnauthorized());
  }
}
