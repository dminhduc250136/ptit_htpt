package com.ptit.htpt.productservice.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.repository.ProductRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
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
 * Phase 19 / Plan 03 — IT cho POST /admin/products/batch.
 *
 * <p>Coverage:
 * 5. happy: 5 SP, body {ids:[id1,id3]} + admin → length=2 với name/brand/thumbnailUrl
 * 6. empty input {ids:[]} → 200 với data=[]
 * 7. no Bearer → 401
 * 8. mix existing + fake UUID → 200, chỉ existing trả về
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminProductBatchControllerIT {

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
  @Autowired ProductRepository productRepo;
  @MockBean RestTemplate restTemplate;
  @Value("${app.jwt.secret}") String jwtSecret;

  @BeforeEach
  void cleanSlate() throws Exception {
    productRepo.deleteAll();
    productRepo.flush();
    insertCategory("cat-batch-it", "Batch IT Cat", "batch-it-cat-" + System.nanoTime());
  }

  private String makeToken(String role) {
    var key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    return Jwts.builder().subject("admin-user")
        .claim("username", "admin")
        .claim("roles", role)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 3600_000L))
        .signWith(key, Jwts.SIG.HS256).compact();
  }

  private ProductEntity seedProduct(String tag) {
    ProductEntity p = ProductEntity.create(
        "Phone " + tag,
        "slug-" + tag + "-" + System.nanoTime(),
        "cat-batch-it",
        new BigDecimal("100.00"),
        "ACTIVE",
        "Brand-" + tag,
        "https://thumb/" + tag + ".webp",
        null, null);
    productRepo.save(p);
    productRepo.flush();
    return p;
  }

  @Test
  void batch_happyPath_returnsRequestedSummaries() throws Exception {
    ProductEntity p1 = seedProduct("a");
    seedProduct("b");
    ProductEntity p3 = seedProduct("c");
    seedProduct("d");
    seedProduct("e");
    String token = makeToken("ADMIN");
    String body = objectMapper.writeValueAsString(Map.of("ids", List.of(p1.id(), p3.id())));

    mockMvc.perform(post("/admin/products/batch")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[*].name").exists())
        .andExpect(jsonPath("$.data[*].brand").exists())
        .andExpect(jsonPath("$.data[*].thumbnailUrl").exists());
  }

  @Test
  void batch_emptyInput_returnsEmpty() throws Exception {
    seedProduct("a");
    String token = makeToken("ADMIN");
    String body = objectMapper.writeValueAsString(Map.of("ids", List.of()));

    mockMvc.perform(post("/admin/products/batch")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void batch_noBearer_returns401() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("ids", List.of("any")));
    mockMvc.perform(post("/admin/products/batch")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void batch_missingIds_returnsOnlyExisting() throws Exception {
    ProductEntity p1 = seedProduct("a");
    seedProduct("b");
    String token = makeToken("ADMIN");
    String body = objectMapper.writeValueAsString(
        Map.of("ids", List.of(p1.id(), "fake-uuid-does-not-exist")));

    mockMvc.perform(post("/admin/products/batch")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(p1.id()));
  }

  private void insertCategory(String id, String name, String slug) throws Exception {
    try (Connection conn = postgres.createConnection("?currentSchema=product_svc");
         Statement stmt = conn.createStatement()) {
      stmt.execute("INSERT INTO product_svc.categories (id, name, slug, deleted, created_at, updated_at) "
          + "VALUES ('" + id + "', '" + name + "', '" + slug + "', FALSE, NOW(), NOW()) "
          + "ON CONFLICT (id) DO NOTHING");
    }
  }
}
