package com.ptit.htpt.productservice.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import static org.mockito.Mockito.when;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReviewControllerTest {

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
    CategoryEntity cat = CategoryEntity.create("Cat-CT", "cat-ct-" + System.nanoTime());
    categoryRepo.save(cat);
    product = ProductEntity.create(
        "P-CT", "p-ct-" + System.nanoTime(),
        cat.id(), new BigDecimal("100000.00"), "ACTIVE",
        null, null, null, null);
    productRepo.save(product);
  }

  private void mockEligibility(boolean eligible) {
    Map<String, Object> data = new HashMap<>();
    data.put("eligible", eligible);
    Map<String, Object> response = new HashMap<>();
    response.put("data", data);
    when(restTemplate.getForObject(
        ArgumentMatchers.anyString(),
        ArgumentMatchers.eq(Map.class),
        ArgumentMatchers.anyString(),
        ArgumentMatchers.anyString()))
      .thenReturn(response);
  }

  private String seedReview(String userId, int rating) {
    ReviewEntity r = ReviewEntity.create(product.id(), userId, "Reviewer-" + userId, rating, "content");
    reviewRepo.save(r);
    return r.id();
  }

  private String makeToken(String userId, String username, String name) {
    var key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    var builder = Jwts.builder().subject(userId)
        .claim("username", username)
        .claim("roles", "USER")
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 3600_000L));
    if (name != null) builder.claim("name", name);
    return builder.signWith(key, Jwts.SIG.HS256).compact();
  }

  @Test
  void listReviews_emptyProduct_returns200WithEmptyContent() throws Exception {
    mockMvc.perform(get("/products/some-product-id/reviews?page=0&size=10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void eligibility_missingAuth_returns401() throws Exception {
    mockMvc.perform(get("/products/p-1/reviews/eligibility"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createReview_missingAuth_returns401() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("rating", 5, "content", "ok"));
    mockMvc.perform(post("/products/p-1/reviews")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createReview_invalidRating_returns400() throws Exception {
    String token = makeToken("u-1", "user1", "User One");
    String body = objectMapper.writeValueAsString(Map.of("rating", 7, "content", "ok"));
    mockMvc.perform(post("/products/p-1/reviews")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }

  // =========================================================================
  // Phase 21 / Plan 02 — REV-04 PATCH/DELETE author + REV-05 sort
  // =========================================================================

  @Test
  void editReview_ownerWithinWindow_returns200() throws Exception {
    String rid = seedReview("u-edit-c1", 4);
    String token = makeToken("u-edit-c1", "u-edit-c1", "U");
    String body = objectMapper.writeValueAsString(Map.of("rating", 5, "content", "edited"));

    mockMvc.perform(patch("/products/{pid}/reviews/{rid}", product.id(), rid)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rating").value(5));
  }

  @Test
  void editReview_nonOwner_returns403WithErrorCode() throws Exception {
    String rid = seedReview("u-owner-c", 4);
    String token = makeToken("u-attacker-c", "att", "A");
    String body = objectMapper.writeValueAsString(Map.of("rating", 5));

    mockMvc.perform(patch("/products/{pid}/reviews/{rid}", product.id(), rid)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("REVIEW_NOT_OWNER")));
  }

  @Test
  void editReview_softDeleted_returns422NotFound() throws Exception {
    String rid = seedReview("u-sd-c", 4);
    // Soft-delete trước qua DELETE endpoint
    String token = makeToken("u-sd-c", "u-sd-c", "U");
    mockMvc.perform(delete("/products/{pid}/reviews/{rid}", product.id(), rid)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    // Edit tiếp → 422 REVIEW_NOT_FOUND
    String body = objectMapper.writeValueAsString(Map.of("rating", 5));
    mockMvc.perform(patch("/products/{pid}/reviews/{rid}", product.id(), rid)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("REVIEW_NOT_FOUND")));
  }

  @Test
  void editReview_missingAuth_returns401() throws Exception {
    String rid = seedReview("u-na", 4);
    String body = objectMapper.writeValueAsString(Map.of("rating", 5));
    mockMvc.perform(patch("/products/{pid}/reviews/{rid}", product.id(), rid)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteReview_owner_returns204() throws Exception {
    String rid = seedReview("u-del-ok", 5);
    String token = makeToken("u-del-ok", "u-del-ok", "U");

    mockMvc.perform(delete("/products/{pid}/reviews/{rid}", product.id(), rid)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    ReviewEntity after = reviewRepo.findById(rid).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(after.deletedAt()).isNotNull();
  }

  @Test
  void deleteReview_nonOwner_returns403() throws Exception {
    String rid = seedReview("u-del-owner", 5);
    String token = makeToken("u-del-att", "att", "A");

    mockMvc.perform(delete("/products/{pid}/reviews/{rid}", product.id(), rid)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("REVIEW_NOT_OWNER")));
  }

  @Test
  void getReviews_sortRatingDesc_ordersHighFirst() throws Exception {
    seedReview("u-s-1", 1);
    seedReview("u-s-2", 5);
    seedReview("u-s-3", 3);

    mockMvc.perform(get("/products/{pid}/reviews?sort=rating_desc&page=0&size=10", product.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].rating").value(5))
        .andExpect(jsonPath("$.data.content[1].rating").value(3))
        .andExpect(jsonPath("$.data.content[2].rating").value(1));
  }

  @Test
  void getReviews_invalidSort_fallbackNewestNot400() throws Exception {
    seedReview("u-fb", 4);

    // KHÔNG 400 — fallback newest (D-12)
    mockMvc.perform(get("/products/{pid}/reviews?sort=garbage_value", product.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.config.editWindowHours").exists());
  }
}
