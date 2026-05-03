package com.ptit.htpt.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ptit.htpt.productservice.domain.CategoryEntity;
import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.domain.ReviewEntity;
import com.ptit.htpt.productservice.repository.CategoryRepository;
import com.ptit.htpt.productservice.repository.ProductRepository;
import com.ptit.htpt.productservice.repository.ReviewRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ReviewServiceTest {

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

  @Autowired ReviewService reviewService;
  @Autowired ReviewRepository reviewRepo;
  @Autowired ProductRepository productRepo;
  @Autowired CategoryRepository categoryRepo;
  @MockBean RestTemplate restTemplate;

  ProductEntity product;

  @BeforeEach
  void setup() {
    reviewRepo.deleteAll();
    // Tạo category + product test
    CategoryEntity category = CategoryEntity.create("Test Cat", "test-cat-" + System.nanoTime());
    categoryRepo.save(category);

    product = ProductEntity.create(
        "Test Product", "test-product-" + System.nanoTime(),
        category.id(), new BigDecimal("100000.00"), "ACTIVE",
        null, null, null, null);
    productRepo.save(product);
  }

  private void mockEligibility(boolean eligible) {
    Map<String, Object> data = new HashMap<>();
    data.put("eligible", eligible);
    Map<String, Object> response = new HashMap<>();
    response.put("data", data);
    when(restTemplate.getForObject(
        anyString(),
        eq(Map.class),
        anyString(),
        anyString()))
      .thenReturn(response);
  }

  @Test
  void createReview_xssPayloadStripped() {
    mockEligibility(true);
    String payload = "<script>alert(1)</script>Hello<b>!</b>";

    Map<String, Object> result = reviewService.createReview(
        product.id(), "u-xss-1", "User One", 5, payload);

    assertThat((String) result.get("content")).doesNotContain("<script>");
    assertThat((String) result.get("content")).doesNotContain("<b>");
    assertThat((String) result.get("content")).contains("Hello");
  }

  @Test
  void createReview_notEligible_throws422() {
    mockEligibility(false);
    assertThatThrownBy(() -> reviewService.createReview(
            product.id(), "u-ineligible-1", "User Two", 4, "good"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
          assertThat(rse.getReason()).contains("REVIEW_NOT_ELIGIBLE");
        });
  }

  @Test
  void createReview_duplicate_throws409() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-dup-1", "User Three", 5, "first");

    assertThatThrownBy(() -> reviewService.createReview(
            product.id(), "u-dup-1", "User Three", 4, "second"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(rse.getReason()).contains("REVIEW_ALREADY_EXISTS");
        });
  }

  @Test
  void createReview_recomputesAvgRating() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-avg-1", "U4", 5, null);
    reviewService.createReview(product.id(), "u-avg-2", "U5", 3, null);

    ProductEntity updated = productRepo.findById(product.id()).orElseThrow();
    assertThat(updated.avgRating().doubleValue()).isEqualTo(4.0);
    assertThat(updated.reviewCount()).isEqualTo(2);
  }

  // ===========================================================================
  // Phase 21 / Plan 02 — REV-04 / REV-05 / REV-06 tests
  // ===========================================================================

  /** Helper: tạo 1 review trực tiếp qua repo, bỏ qua eligibility check, return id. */
  private String seedReview(String userId, int rating, String content) {
    ReviewEntity r = ReviewEntity.create(product.id(), userId, "Reviewer-" + userId, rating, content);
    reviewRepo.save(r);
    return r.id();
  }

  // ---- editReview ---------------------------------------------------------

  @Test
  void editReview_ownerWithinWindow_returnsUpdated() {
    String rid = seedReview("u-edit-1", 4, "first content");

    Map<String, Object> result = reviewService.editReview(rid, "u-edit-1", 5, "edited content");

    assertThat(result.get("rating")).isEqualTo(5);
    assertThat((String) result.get("content")).contains("edited");
  }

  @Test
  void editReview_nonOwner_returns403() {
    String rid = seedReview("u-owner", 4, "content");

    assertThatThrownBy(() -> reviewService.editReview(rid, "u-attacker", 5, "hacked"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(rse.getReason()).contains("REVIEW_NOT_OWNER");
        });
  }

  @Test
  void editReview_softDeletedReview_returns422NotFound() {
    String rid = seedReview("u-del-1", 4, "content");
    // Soft-delete trước
    reviewService.softDeleteReview(rid, "u-del-1");

    assertThatThrownBy(() -> reviewService.editReview(rid, "u-del-1", 5, "edit"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
          assertThat(rse.getReason()).contains("REVIEW_NOT_FOUND");
        });
  }

  @Test
  void editReview_contentOnly_skipsRecomputeAvgUnchanged() {
    mockEligibility(true);
    // Setup: 2 review với rating khác nhau → avg = 3.5, count = 2
    reviewService.createReview(product.id(), "u-co-1", "U", 5, "five");
    reviewService.createReview(product.id(), "u-co-2", "U", 2, "two");
    BigDecimal avgBefore = productRepo.findById(product.id()).orElseThrow().avgRating();

    // Pick rid của review user u-co-1
    String rid = reviewRepo.findAll().stream()
        .filter(r -> "u-co-1".equals(r.userId())).findFirst().orElseThrow().id();
    int oldRating = 5;

    // Edit content-only (rating null) — KHÔNG trigger recompute logic ngay cả nếu chạy
    reviewService.editReview(rid, "u-co-1", null, "new content only");

    BigDecimal avgAfter = productRepo.findById(product.id()).orElseThrow().avgRating();
    // Pitfall 2: rating không đổi → avg cùng kết quả (recompute path KHÔNG được gọi)
    assertThat(avgAfter).isEqualByComparingTo(avgBefore);
    // Verify content thực sự đổi
    ReviewEntity updated = reviewRepo.findById(rid).orElseThrow();
    assertThat(updated.content()).isEqualTo("new content only");
    assertThat(updated.rating()).isEqualTo(oldRating);
  }

  @Test
  void editReview_ratingChanged_triggersRecompute() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-rc-1", "U", 5, null);
    reviewService.createReview(product.id(), "u-rc-2", "U", 5, null);
    // Initial avg = 5.0
    assertThat(productRepo.findById(product.id()).orElseThrow().avgRating().doubleValue())
        .isEqualTo(5.0);

    String rid = reviewRepo.findAll().stream()
        .filter(r -> "u-rc-1".equals(r.userId())).findFirst().orElseThrow().id();

    reviewService.editReview(rid, "u-rc-1", 1, null);

    BigDecimal avgAfter = productRepo.findById(product.id()).orElseThrow().avgRating();
    // (5 + 1) / 2 = 3.0
    assertThat(avgAfter.doubleValue()).isEqualTo(3.0);
  }

  // ---- softDeleteReview ---------------------------------------------------

  @Test
  void softDeleteReview_owner_marksDeletedAndRecomputes() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-sd-1", "U", 5, null);
    reviewService.createReview(product.id(), "u-sd-2", "U", 3, null);
    String rid = reviewRepo.findAll().stream()
        .filter(r -> "u-sd-1".equals(r.userId())).findFirst().orElseThrow().id();

    reviewService.softDeleteReview(rid, "u-sd-1");

    ReviewEntity after = reviewRepo.findById(rid).orElseThrow();
    assertThat(after.deletedAt()).isNotNull();
    // Recompute scope: chỉ còn review rating=3 còn active → avg=3.0, count=1
    ProductEntity p = productRepo.findById(product.id()).orElseThrow();
    assertThat(p.avgRating().doubleValue()).isEqualTo(3.0);
    assertThat(p.reviewCount()).isEqualTo(1);
  }

  @Test
  void softDeleteReview_nonOwner_returns403() {
    String rid = seedReview("u-owner-2", 4, "x");
    assertThatThrownBy(() -> reviewService.softDeleteReview(rid, "u-attacker-2"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(rse.getReason()).contains("REVIEW_NOT_OWNER");
        });
  }

  // ---- setVisibility ------------------------------------------------------

  @Test
  void setVisibility_hide_recomputesAvgExcludingHidden() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-vis-1", "U", 5, null);
    reviewService.createReview(product.id(), "u-vis-2", "U", 1, null);
    // avg = 3.0
    String ridLow = reviewRepo.findAll().stream()
        .filter(r -> "u-vis-2".equals(r.userId())).findFirst().orElseThrow().id();

    reviewService.setVisibility(ridLow, true);

    ReviewEntity hidden = reviewRepo.findById(ridLow).orElseThrow();
    assertThat(hidden.hidden()).isTrue();
    ProductEntity p = productRepo.findById(product.id()).orElseThrow();
    // Hidden review excluded → còn rating=5 active visible → avg=5.0, count=1
    assertThat(p.avgRating().doubleValue()).isEqualTo(5.0);
    assertThat(p.reviewCount()).isEqualTo(1);
  }

  // ---- hardDelete ---------------------------------------------------------

  @Test
  void hardDelete_removesRowAndRecomputes() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-hd-1", "U", 4, null);
    reviewService.createReview(product.id(), "u-hd-2", "U", 2, null);
    // avg = 3.0
    String rid = reviewRepo.findAll().stream()
        .filter(r -> "u-hd-1".equals(r.userId())).findFirst().orElseThrow().id();

    reviewService.hardDelete(rid);

    assertThat(reviewRepo.findById(rid)).isEmpty();
    ProductEntity p = productRepo.findById(product.id()).orElseThrow();
    // Còn rating=2 → avg=2.0, count=1
    assertThat(p.avgRating().doubleValue()).isEqualTo(2.0);
    assertThat(p.reviewCount()).isEqualTo(1);
  }

  // ---- listReviews sort + config ------------------------------------------

  @Test
  void listReviews_sortRatingDesc_ordersHighFirst() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-s-1", "U", 1, null);
    reviewService.createReview(product.id(), "u-s-2", "U", 5, null);
    reviewService.createReview(product.id(), "u-s-3", "U", 3, null);

    Map<String, Object> result = reviewService.listReviews(product.id(), 0, 10, "rating_desc");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");

    assertThat(content).hasSize(3);
    assertThat(content.get(0).get("rating")).isEqualTo(5);
    assertThat(content.get(1).get("rating")).isEqualTo(3);
    assertThat(content.get(2).get("rating")).isEqualTo(1);
  }

  @Test
  void listReviews_invalidSort_fallbackNewestNoThrow() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-fb-1", "U", 4, null);

    // KHÔNG throw — fallback newest (D-13)
    Map<String, Object> result = reviewService.listReviews(product.id(), 0, 10, "garbage_value");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
    assertThat(content).hasSize(1);
    // Config embedded for FE (D-02)
    @SuppressWarnings("unchecked")
    Map<String, Object> config = (Map<String, Object>) result.get("config");
    assertThat(config).isNotNull();
    assertThat(config.get("editWindowHours")).isNotNull();
  }

  @Test
  void listReviews_excludesDeletedAndHidden() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-ex-1", "U", 5, null);
    reviewService.createReview(product.id(), "u-ex-2", "U", 4, null);
    reviewService.createReview(product.id(), "u-ex-3", "U", 3, null);
    String ridDeleted = reviewRepo.findAll().stream()
        .filter(r -> "u-ex-1".equals(r.userId())).findFirst().orElseThrow().id();
    String ridHidden = reviewRepo.findAll().stream()
        .filter(r -> "u-ex-2".equals(r.userId())).findFirst().orElseThrow().id();

    reviewService.softDeleteReview(ridDeleted, "u-ex-1");
    reviewService.setVisibility(ridHidden, true);

    Map<String, Object> result = reviewService.listReviews(product.id(), 0, 10, "newest");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
    // Chỉ còn 1 review active+visible
    assertThat(content).hasSize(1);
    assertThat(content.get(0).get("rating")).isEqualTo(3);
  }

  // ---- recompute scope ----------------------------------------------------

  @Test
  void recompute_resetsToZero_whenAllReviewsDeleted() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-z-1", "U", 5, null);
    String rid = reviewRepo.findAll().stream()
        .filter(r -> "u-z-1".equals(r.userId())).findFirst().orElseThrow().id();

    reviewService.softDeleteReview(rid, "u-z-1");

    ProductEntity p = productRepo.findById(product.id()).orElseThrow();
    assertThat(p.avgRating().doubleValue()).isEqualTo(0.0);
    assertThat(p.reviewCount()).isEqualTo(0);
  }

  // ---- listAdminReviews ---------------------------------------------------

  @Test
  void listAdminReviews_filterAll_includesDeletedAndHiddenWithSlug() {
    mockEligibility(true);
    reviewService.createReview(product.id(), "u-ar-1", "U", 5, null);
    reviewService.createReview(product.id(), "u-ar-2", "U", 4, null);
    String rid1 = reviewRepo.findAll().stream()
        .filter(r -> "u-ar-1".equals(r.userId())).findFirst().orElseThrow().id();
    reviewService.softDeleteReview(rid1, "u-ar-1");

    Map<String, Object> result = reviewService.listAdminReviews(0, 20, "all");
    @SuppressWarnings("unchecked")
    List<AdminReviewDTO> content = (List<AdminReviewDTO>) result.get("content");

    assertThat(content).hasSize(2);
    // Cả 2 review đều phải có productSlug (product chưa soft-delete)
    assertThat(content).allSatisfy(d -> assertThat(d.productSlug()).isNotNull());
    // Có ít nhất 1 deleted
    assertThat(content).anyMatch(d -> d.deletedAt() != null);
  }
}
