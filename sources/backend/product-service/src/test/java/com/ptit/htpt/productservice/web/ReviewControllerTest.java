package com.ptit.htpt.productservice.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.htpt.productservice.repository.ReviewRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
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
  @MockBean RestTemplate restTemplate;
  @Value("${app.jwt.secret}") String jwtSecret;

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
}
