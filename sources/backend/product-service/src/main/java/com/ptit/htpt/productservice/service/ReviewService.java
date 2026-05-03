package com.ptit.htpt.productservice.service;

import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.domain.ReviewEntity;
import com.ptit.htpt.productservice.repository.AdminReviewSpecifications;
import com.ptit.htpt.productservice.repository.ProductRepository;
import com.ptit.htpt.productservice.repository.ReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 21 / Plan 02: review CRUD + moderation lifecycle.
 *
 * <p>Recompute invariant (D-08): mọi mutation path thay đổi rating effective
 * (create / edit-with-rating-change / softDelete / setVisibility / hardDelete)
 * gọi {@link #recomputeProductRating(String)} để đồng bộ avg + count trên ProductEntity.
 * Edit content-only (rating giữ nguyên) KHÔNG recompute (Pitfall 2 optimization).
 */
@Service
public class ReviewService {
  private static final String ORDER_ELIGIBILITY_URL =
      "http://order-service:8080/internal/orders/eligibility?userId={userId}&productId={productId}";

  private final ReviewRepository reviewRepo;
  private final ProductRepository productRepo;
  private final RestTemplate restTemplate;
  private final long editWindowHours;

  public ReviewService(
      ReviewRepository reviewRepo,
      ProductRepository productRepo,
      RestTemplate restTemplate,
      @Value("${app.reviews.edit-window-hours:24}") long editWindowHours) {
    this.reviewRepo = reviewRepo;
    this.productRepo = productRepo;
    this.restTemplate = restTemplate;
    this.editWindowHours = editWindowHours;
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
      // Pitfall 5: order-svc down → fail-safe false
      return false;
    }
  }

  @Transactional
  public Map<String, Object> createReview(String productId, String userId, String reviewerName,
                                          int rating, String content) {
    // Pre-check product tồn tại
    productRepo.findById(productId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

    // Pitfall 3: pre-check duplicate trước khi insert.
    // Phase 21 D-06: chỉ check ACTIVE review (deleted_at IS NULL) — cho phép re-review sau soft-delete.
    if (reviewRepo.existsByProductIdAndUserIdAndDeletedAtIsNull(productId, userId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS");
    }

    // D-03: BE re-check eligibility (không trust FE)
    if (!checkEligibilityInternal(userId, productId)) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_NOT_ELIGIBLE");
    }

    // D-14: Jsoup sanitize. Pitfall A3: handle null/blank trước.
    String sanitized = sanitize(content);

    ReviewEntity review = ReviewEntity.create(productId, userId, reviewerName, rating, sanitized);
    reviewRepo.save(review);

    // D-12: recompute từ scratch trong cùng transaction.
    recomputeProductRating(productId);

    return toResponse(review);
  }

  /**
   * Phase 21 REV-04: owner edit trong cửa sổ {@code editWindowHours} giờ kể từ createdAt.
   * Pitfall 2: chỉ recompute khi rating thay đổi (content-only edit không ảnh hưởng avg).
   */
  @Transactional
  public Map<String, Object> editReview(String reviewId, String userId, Integer newRating, String content) {
    ReviewEntity review = reviewRepo.findByIdAndDeletedAtIsNull(reviewId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_NOT_FOUND"));
    if (!review.userId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "REVIEW_NOT_OWNER");
    }
    Instant deadline = review.createdAt().plus(editWindowHours, ChronoUnit.HOURS);
    if (Instant.now().isAfter(deadline)) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_EDIT_WINDOW_EXPIRED");
    }
    int oldRating = review.rating();
    String sanitized = sanitize(content);
    int effectiveRating = (newRating != null) ? newRating : oldRating;
    review.applyEdit(effectiveRating, sanitized);
    reviewRepo.save(review);
    if (newRating != null && newRating.intValue() != oldRating) {
      recomputeProductRating(review.productId());   // Pitfall 2 optimization
    }
    return toResponse(review);
  }

  /** Phase 21 REV-04: owner soft-delete (deleted_at = NOW). */
  @Transactional
  public void softDeleteReview(String reviewId, String userId) {
    ReviewEntity review = reviewRepo.findByIdAndDeletedAtIsNull(reviewId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_NOT_FOUND"));
    if (!review.userId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "REVIEW_NOT_OWNER");
    }
    review.markDeleted();
    reviewRepo.save(review);
    recomputeProductRating(review.productId());
  }

  /**
   * Phase 21 REV-05: public list với sort key + pagination.
   * Repo finder loại deleted + hidden ở SQL level. Embed config.editWindowHours cho FE (D-02).
   */
  public Map<String, Object> listReviews(String productId, int page, int size, String sortKey) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(1, size), 50);
    Pageable pageable = PageRequest.of(safePage, safeSize, resolveSort(sortKey));
    Page<ReviewEntity> result =
        reviewRepo.findByProductIdAndDeletedAtIsNullAndHiddenFalse(productId, pageable);

    List<Map<String, Object>> items = result.getContent().stream().map(this::toResponse).toList();

    Map<String, Object> map = new LinkedHashMap<>();
    map.put("content", items);
    map.put("totalElements", result.getTotalElements());
    map.put("totalPages", result.getTotalPages());
    map.put("currentPage", safePage);
    map.put("pageSize", safeSize);
    map.put("isFirst", safePage <= 0);
    map.put("isLast", safePage >= Math.max(result.getTotalPages() - 1, 0));
    map.put("config", Map.of("editWindowHours", editWindowHours));
    return map;
  }

  /**
   * Phase 21 REV-06: admin list paginated với 4-state filter (all/visible/hidden/deleted).
   * Slug resolution batch — productRepo.findAllById skip product đã soft-delete (@SQLRestriction)
   * → idToSlug.get(...) trả null cho các review của product đã xoá; FE render "—" (Finding 9).
   */
  public Map<String, Object> listAdminReviews(int page, int size, String filter) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(1, size), 100);
    Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt")));
    Page<ReviewEntity> result = reviewRepo.findAll(AdminReviewSpecifications.withFilter(filter), pageable);

    List<String> productIds = result.getContent().stream()
        .map(ReviewEntity::productId).distinct().toList();
    Map<String, String> idToSlug = productIds.isEmpty()
        ? Map.of()
        : productRepo.findAllById(productIds).stream()
            .collect(Collectors.toMap(ProductEntity::id, ProductEntity::slug));

    List<AdminReviewDTO> dtos = result.getContent().stream()
        .map(r -> AdminReviewDTO.from(r, idToSlug.get(r.productId())))
        .toList();

    Map<String, Object> map = new LinkedHashMap<>();
    map.put("content", dtos);
    map.put("totalElements", result.getTotalElements());
    map.put("totalPages", result.getTotalPages());
    map.put("currentPage", safePage);
    map.put("pageSize", safeSize);
    map.put("isFirst", safePage <= 0);
    map.put("isLast", safePage >= Math.max(result.getTotalPages() - 1, 0));
    return map;
  }

  /** Phase 21 REV-06: admin hide/unhide review (luôn recompute vì rating effective đổi). */
  @Transactional
  public void setVisibility(String reviewId, boolean hidden) {
    ReviewEntity review = reviewRepo.findById(reviewId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND"));
    review.setHidden(hidden);
    reviewRepo.save(review);
    recomputeProductRating(review.productId());
  }

  /** Phase 21 REV-06: admin hard-delete (capture productId trước khi delete để tránh detached-entity). */
  @Transactional
  public void hardDelete(String reviewId) {
    ReviewEntity review = reviewRepo.findById(reviewId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND"));
    String productId = review.productId();
    reviewRepo.delete(review);
    recomputeProductRating(productId);
  }

  /**
   * Recompute helper cô lập (D-12). Excludes deleted + hidden reviews qua repo.computeStats JPQL.
   * Reset to ZERO khi count=0 (Phase 21-01 Task 0 A8 — ProductEntity.updateRatingStats null-safe).
   *
   * <p>Edge case: nếu product đã soft-delete (@SQLRestriction trên ProductEntity skip findById),
   * orElseThrow → 404. Acceptable cho admin mutation (chấp nhận edge case khi review của product
   * đã xoá — rare; threat T-21-02-09).
   */
  private void recomputeProductRating(String productId) {
    ProductEntity product = productRepo.findById(productId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    Object[] row = reviewRepo.computeStats(productId);
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

  /** D-13 sort resolver: invalid value fallback newest (KHÔNG throw 400). */
  private static Sort resolveSort(String sortKey) {
    return switch (sortKey == null ? "newest" : sortKey) {
      case "rating_desc" -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"));
      case "rating_asc"  -> Sort.by(Sort.Order.asc("rating"),  Sort.Order.desc("createdAt"));
      default            -> Sort.by(Sort.Order.desc("createdAt"));
    };
  }

  /** D-14 Jsoup sanitize: strip toàn bộ HTML; null/blank input → null output. */
  private static String sanitize(String content) {
    if (content == null || content.isBlank()) return null;
    String cleaned = Jsoup.clean(content, Safelist.none());
    return cleaned.isBlank() ? null : cleaned;
  }

  private Map<String, Object> toResponse(ReviewEntity r) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", r.id());
    map.put("productId", r.productId());
    map.put("userId", r.userId());
    map.put("reviewerName", r.reviewerName());
    map.put("rating", r.rating());
    map.put("content", r.content());
    map.put("hidden", r.hidden());
    map.put("deletedAt", r.deletedAt() != null ? r.deletedAt().toString() : null);
    map.put("createdAt", r.createdAt() != null ? r.createdAt().toString() : null);
    map.put("updatedAt", r.updatedAt() != null ? r.updatedAt().toString() : null);
    return map;
  }
}
