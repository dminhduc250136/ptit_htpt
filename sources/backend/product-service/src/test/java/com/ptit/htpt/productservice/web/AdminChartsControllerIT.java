package com.ptit.htpt.productservice.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.repository.ProductRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 19 / Plan 03 — IT cho /admin/products/charts/low-stock.
 *
 * <p>Coverage:
 * 1. admin Bearer + 3 SP stock=[3,5,8] → 200, length=3 sorted ASC
 * 2. empty → 200 với data=[]
 * 3. no Bearer → 401
 * 4. USER role → 403
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminChartsControllerIT {

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
  @Autowired ProductRepository productRepo;
  @MockBean RestTemplate restTemplate;
  @Value("${app.jwt.secret}") String jwtSecret;

  @BeforeEach
  void cleanSlate() throws Exception {
    productRepo.deleteAll();
    productRepo.flush();
    insertCategory("cat-charts-it", "Charts IT Cat", "charts-it-cat-" + System.nanoTime());
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

  private void seedProduct(String tag, int stock) {
    ProductEntity p = ProductEntity.create(
        "Product " + tag,
        "slug-" + tag + "-" + System.nanoTime(),
        "cat-charts-it",
        new BigDecimal("100.00"),
        "ACTIVE",
        "BrandY",
        "https://thumb/" + tag + ".webp",
        null, null);
    p.setStock(stock);
    productRepo.save(p);
    productRepo.flush();
  }

  @Test
  void lowStock_admin_returnsSortedAsc() throws Exception {
    seedProduct("a", 3);
    seedProduct("b", 5);
    seedProduct("c", 8);
    String token = makeToken("ADMIN");

    mockMvc.perform(get("/admin/products/charts/low-stock")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(3))
        .andExpect(jsonPath("$.data[0].stock").value(3))
        .andExpect(jsonPath("$.data[1].stock").value(5))
        .andExpect(jsonPath("$.data[2].stock").value(8));
  }

  @Test
  void lowStock_empty_returnsEmptyArray() throws Exception {
    seedProduct("over", 50);
    String token = makeToken("ADMIN");

    mockMvc.perform(get("/admin/products/charts/low-stock")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void lowStock_noBearer_returns401() throws Exception {
    mockMvc.perform(get("/admin/products/charts/low-stock"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void lowStock_userRole_returns403() throws Exception {
    String token = makeToken("USER");
    mockMvc.perform(get("/admin/products/charts/low-stock")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
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
