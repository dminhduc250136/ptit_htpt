package com.ptit.htpt.productservice.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.htpt.productservice.domain.CategoryEntity;
import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.repository.InMemoryProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductControllerSlugTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private InMemoryProductRepository repository;

  private static final String SEED_SLUG = "uat-test-slug-product";

  @BeforeEach
  void seedProduct() {
    // Seed a category first so toResponse can serialize category.name + category.slug.
    CategoryEntity category = CategoryEntity.create("Test Category", null, "ACTIVE");
    repository.saveCategory(category);

    ProductEntity product = ProductEntity.create(
        "UAT Test Slug Product",
        SEED_SLUG,
        category.id(),
        new BigDecimal("99000"),
        "ACTIVE"
    );
    repository.saveProduct(product);
  }

  @Test
  void getProductBySlug_returns200_withRichShape() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/products/slug/" + SEED_SLUG, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).isNotNull();
    // Envelope check (success ApiResponse: { timestamp, status, message, data } — no `code` on success)
    assertThat(body).contains("\"status\":200");
    assertThat(body).contains("\"message\":\"Product loaded\"");
    assertThat(body).doesNotContain("\"INTERNAL_ERROR\"");
    assertThat(body).doesNotContain("\"NOT_FOUND\"");
    // Rich-shape fields the FE consumes
    assertThat(body).contains("\"category\":");
    assertThat(body).contains("\"name\":\"Test Category\"");
    assertThat(body).contains("\"thumbnailUrl\":");
    assertThat(body).contains("\"rating\":");
    assertThat(body).contains("\"reviewCount\":");
    assertThat(body).contains("\"tags\":");
    assertThat(body).contains("\"slug\":\"" + SEED_SLUG + "\"");
  }

  @Test
  void getProductBySlug_returns404_whenSlugNotFound() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/products/slug/no-such-slug-exists", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("\"code\":\"NOT_FOUND\"");
  }
}
