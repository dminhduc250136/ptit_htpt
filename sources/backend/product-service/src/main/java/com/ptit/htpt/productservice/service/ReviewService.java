package com.ptit.htpt.productservice.service;

import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.domain.ReviewEntity;
import com.ptit.htpt.productservice.repository.ProductRepository;
import com.ptit.htpt.productservice.repository.ReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {
  private static final String ORDER_ELIGIBILITY_URL =
      "http://order-service:8080/internal/orders/eligibility?userId={userId}&productId={productId}";

  private final ReviewRepository reviewRepo;
  private final ProductRepository productRepo;
  private final RestTemplate restTemplate;

  public ReviewService(ReviewRepository reviewRepo, ProductRepository productRepo,
                       RestTemplate restTemplate) {
    this.reviewRepo = reviewRepo;
    this.productRepo = productRepo;
    this.restTemplate = restTemplate;
  }

  /** D-08/D-03: gọi order-svc internal endpoint. Fail-safe: exception → false (Pitfall 5). */
  public boolean checkEligibilityInternal(String userId, String productId) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response = restTemplate.getForObject(
          ORDER_ELIGIBILITY_URL, Map.class, userId, productId);
      if (response == null) return false;
      Object data = response.get("data");
      if (!(data instanceof Map<?, ?> dataMap)) return false;
      Object eligible = dataMap.get("eligible");
      return Boolean.TRUE.equals(eligible);
    } catch (RestClientException ex) {
      // Pitfall 5: order-svc down → log warning, return false fail-safe
      return false;
    }
  }

  @Transactional
  public Map<String, Object> createReview(String productId, String userId, String reviewerName,
                                          int rating, String content) {
    // Pre-check product tồn tại
    ProductEntity product = productRepo.findById(productId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

    // Pitfall 3: pre-check duplicate trước khi insert (GlobalExceptionHandler không catch DataIntegrityViolationException)
    if (reviewRepo.existsByProductIdAndUserId(productId, userId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS");
    }

    // D-03: BE re-check eligibility (không trust FE)
    if (!checkEligibilityInternal(userId, productId)) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_NOT_ELIGIBLE");
    }

    // D-14: Jsoup sanitize. Pitfall A3: handle null/blank trước.
    String sanitized = null;
    if (content != null && !content.isBlank()) {
      String cleaned = Jsoup.clean(content, Safelist.none());
      sanitized = cleaned.isBlank() ? null : cleaned;
    }

    ReviewEntity review = ReviewEntity.create(productId, userId, reviewerName, rating, sanitized);
    reviewRepo.save(review);

    // D-12: recompute from scratch trong cùng transaction (không drift)
    recomputeProductRating(product);

    return toResponse(review);
  }

  public Map<String, Object> listReviews(String productId, int page, int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(1, size), 50);
    Page<ReviewEntity> result = reviewRepo.findByProductIdOrderByCreatedAtDesc(
        productId, PageRequest.of(safePage, safeSize));

    List<Map<String, Object>> items = result.getContent().stream().map(this::toResponse).toList();

    Map<String, Object> map = new LinkedHashMap<>();
    map.put("content", items);
    map.put("totalElements", result.getTotalElements());
    map.put("totalPages", result.getTotalPages());
    map.put("currentPage", safePage);
    map.put("pageSize", safeSize);
    map.put("isFirst", safePage <= 0);
    map.put("isLast", safePage >= Math.max(result.getTotalPages() - 1, 0));
    return map;
  }

  /** Recompute SELECT AVG (COALESCE 0), COUNT — D-12. */
  private void recomputeProductRating(ProductEntity product) {
    Object[] row = reviewRepo.computeStats(product.id());
    BigDecimal avg = BigDecimal.ZERO;
    int count = 0;
    if (row != null && row.length >= 2) {
      Object avgObj = row[0];
      Object cntObj = row[1];
      if (avgObj instanceof Number n) {
        avg = BigDecimal.valueOf(n.doubleValue()).setScale(1, RoundingMode.HALF_UP);
      }
      if (cntObj instanceof Number c) {
        count = c.intValue();
      }
    }
    product.updateRatingStats(avg, count);
    productRepo.save(product);
  }

  private Map<String, Object> toResponse(ReviewEntity r) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", r.id());
    map.put("productId", r.productId());
    map.put("userId", r.userId());
    map.put("reviewerName", r.reviewerName());
    map.put("rating", r.rating());
    map.put("content", r.content());
    map.put("createdAt", r.createdAt() != null ? r.createdAt().toString() : null);
    return map;
  }
}
