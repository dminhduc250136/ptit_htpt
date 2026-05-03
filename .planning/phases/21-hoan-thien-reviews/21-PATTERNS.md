# Phase 21: Hoàn Thiện Reviews — Pattern Map

**Mapped:** 2026-05-02
**Files analyzed:** 21 file (CREATE / MODIFY / TOUCH theo File-Level Change Inventory của RESEARCH §"File-Level Change Inventory")
**Analogs found:** 19 / 21 (2 file là TOUCH/MODIFY tự self, không cần analog ngoài)
**Phase boundary:** REV-04 (author edit/delete + 24h window) · REV-05 (sort dropdown) · REV-06 (admin moderation `/admin/reviews`)

> **Đính chính quan trọng từ RESEARCH Finding 1:** Gateway KHÔNG có `/api/admin/**`. Pattern thực tế là `/api/{service}/admin/**` → rewrite `/admin/{service}/**`. AdminReviewController PHẢI dùng `@RequestMapping("/admin/products/reviews")`. FE gọi `/api/products/admin/reviews/...`. Quy ước này đè CONTEXT D-18.

---

## File Classification

### Backend (`sources/backend/product-service/`)

| File | Action | Role | Data Flow | Closest Analog | Match |
|------|--------|------|-----------|----------------|-------|
| `src/main/resources/db/migration/V7__add_review_moderation_columns.sql` | CREATE | migration | batch | `V3__add_product_stock.sql` + `V4__create_reviews.sql` (partial UNIQUE pattern mới — không có analog 1-1) | role-match |
| `src/main/java/com/ptit/htpt/productservice/domain/ReviewEntity.java` | MODIFY | model (entity) | CRUD | self (extend Phase 13 baseline) | self-modify |
| `src/main/java/com/ptit/htpt/productservice/repository/ReviewRepository.java` | MODIFY | repository | CRUD + Specification | self + `ProductRepository` (derived finders) | self-modify + role-match |
| `src/main/java/com/ptit/htpt/productservice/repository/AdminReviewSpecifications.java` | CREATE | repository | query-builder | (no analog — Specifications mới trong codebase) | no-analog |
| `src/main/java/com/ptit/htpt/productservice/service/ReviewService.java` | MODIFY | service | CRUD + transform | self + `ProductCrudService` (admin paginate map) | self-modify |
| `src/main/java/com/ptit/htpt/productservice/service/AdminReviewDTO.java` | CREATE | DTO | transform | `ProductDto.java` (record DTO style) | role-match |
| `src/main/java/com/ptit/htpt/productservice/web/ReviewController.java` | MODIFY | controller | request-response | self + `ProductController` (PATCH/DELETE shapes) | self-modify |
| `src/main/java/com/ptit/htpt/productservice/web/AdminReviewController.java` | CREATE | controller | request-response | `AdminProductController.java` + `AdminStatsController.java` (use of `JwtRoleGuard`) | exact |
| `src/main/resources/application.yml` | TOUCH | config | — | self (key `app.jwt.secret` đã có) | self-modify |
| `src/test/java/.../service/ReviewServiceTest.java` | MODIFY | test | — | self (Phase 13 baseline) | self-modify |
| `src/test/java/.../web/ReviewControllerTest.java` | MODIFY | test | — | self (Phase 13 baseline) | self-modify |
| `src/test/java/.../web/AdminReviewControllerTest.java` | CREATE | test | — | (existing AdminProductController test nếu có) hoặc `ReviewControllerTest` | role-match |
| `src/test/resources/application-test.yml` | TOUCH/CREATE | config | — | (no analog — chỉ thêm key override) | no-analog |

### Frontend (`sources/frontend/src/`)

| File | Action | Role | Data Flow | Closest Analog | Match |
|------|--------|------|-----------|----------------|-------|
| `app/products/[slug]/ReviewSection/ReviewSection.tsx` | MODIFY | component (orchestrator) | request-response | self (Phase 13) | self-modify |
| `app/products/[slug]/ReviewSection/ReviewList.tsx` | MODIFY | component (presenter + sort + actions) | request-response | self + `app/admin/products/page.tsx` (toolbar pattern cho dropdown header) | self-modify + role-match |
| `app/products/[slug]/ReviewSection/ReviewForm.tsx` | MODIFY | component (rhf form) | request-response | self (Phase 13) — thêm prop `mode` + `initialValues` + `onCancel` | self-modify |
| `app/products/[slug]/ReviewSection/ReviewSection.module.css` | MODIFY | styles | — | self | self-modify |
| `app/admin/reviews/page.tsx` | CREATE | page (admin table) | request-response | `app/admin/products/page.tsx` | exact |
| `app/admin/reviews/page.module.css` | CREATE | styles | — | `app/admin/products/page.module.css` | exact (clone) |
| `app/admin/layout.tsx` | TOUCH | layout (sidebar nav) | — | self (insert nav item) | self-modify |
| `services/reviews.ts` | MODIFY | service-fe | request-response | self (Phase 13) + `services/products.ts` (URLSearchParams pattern + admin endpoints) | self-modify + role-match |
| `types/index.ts` | MODIFY | utility (types) | — | self | self-modify |
| `tests/e2e/reviews-author-edit.spec.ts` | CREATE | test (Playwright) | — | existing E2E specs (cùng folder) | role-match |
| `tests/e2e/admin-reviews-moderation.spec.ts` | CREATE | test (Playwright) | — | existing E2E specs | role-match |

---

## Pattern Assignments

### `V7__add_review_moderation_columns.sql` (migration, batch)

**Analog 1 — header + idempotency:** `sources/backend/product-service/src/main/resources/db/migration/V3__add_product_stock.sql` lines 1-3.

```sql
-- Phase 8 / Plan 01 (D-01): Thêm column stock vào product_svc.products.
-- IF NOT EXISTS đảm bảo idempotent khi chạy lại trong test environments.
ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS stock INT NOT NULL DEFAULT 0;
```

**Analog 2 — UNIQUE constraint naming convention:** `V4__create_reviews.sql` (Phase 13) — constraint cũ cần drop tên `uq_review_product_user`.

**V7 cần copy nguyên block từ RESEARCH Finding 4 (đã được verify Postgres-compliant):**

```sql
-- V7__add_review_moderation_columns.sql
-- REV-04 + REV-06: thêm columns moderation, đổi UNIQUE thành partial index để cho phép re-review sau soft-delete.

ALTER TABLE product_svc.reviews
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE NULL,
  ADD COLUMN IF NOT EXISTS hidden     BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE product_svc.reviews
  DROP CONSTRAINT IF EXISTS uq_review_product_user;

CREATE UNIQUE INDEX IF NOT EXISTS uq_review_product_user_active
  ON product_svc.reviews (product_id, user_id)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_reviews_visibility
  ON product_svc.reviews (product_id, hidden, deleted_at);
```

**Quy ước (rút từ V1 + V3 + V6):** schema prefix `product_svc.`, `IF [NOT] EXISTS` cho idempotency, comment header bằng tiếng Việt nêu Phase + mục đích.

---

### `ReviewEntity.java` — MODIFY (model, CRUD)

**Analog:** self (`sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ReviewEntity.java` lines 1-76).

**Existing accessor pattern** (lines 60-68 — record-style, KHÔNG dùng `getXxx`):
```java
public String id() { return id; }
public String productId() { return productId; }
public Instant createdAt() { return createdAt; }
public Instant updatedAt() { return updatedAt; }
```

**Mutator-update-timestamp pattern từ ProductEntity.java** (Phase 13 PATTERNS line 421-426 — `setStock` style):
```java
public void setStock(int stock) {
  this.stock = Math.max(0, stock);
  this.updatedAt = Instant.now();
}
```

**Bổ sung Phase 21 (RESEARCH Finding 5 — verbatim spec):**
```java
@Column(name = "deleted_at")
private Instant deletedAt;        // null = active

@Column(nullable = false)
private boolean hidden = false;

public void markDeleted() {
  this.deletedAt = Instant.now();
  this.updatedAt = this.deletedAt;
}
public void setHidden(boolean hidden) {
  this.hidden = hidden;
  this.updatedAt = Instant.now();
}
public void applyEdit(int newRating, String sanitizedContent) {
  this.rating = newRating;
  this.content = sanitizedContent;
  this.updatedAt = Instant.now();
}
public Instant deletedAt() { return deletedAt; }
public boolean hidden() { return hidden; }
public boolean isActive() { return deletedAt == null && !hidden; }
```

**KHÔNG thêm `@SQLRestriction`** — admin cần đọc soft-deleted (anti-pattern cited trong CONTEXT `<code_context>` lines 224-225).

---

### `ReviewRepository.java` — MODIFY (repository, CRUD + Specification)

**Analog 1 — derived finder:** self lines 12-14 (Phase 13).
```java
Page<ReviewEntity> findByProductIdOrderByCreatedAtDesc(String productId, Pageable pageable);
boolean existsByProductIdAndUserId(String productId, String userId);
```

**Analog 2 — JPQL `computeStats`:** self lines 17-18 (cần update WHERE).
```java
@Query("SELECT COALESCE(AVG(r.rating), 0), COUNT(r) FROM ReviewEntity r WHERE r.productId = :productId")
Object[] computeStats(@Param("productId") String productId);
```

**Mở rộng Phase 21 (RESEARCH Finding 2 — verbatim):**
```java
public interface ReviewRepository extends JpaRepository<ReviewEntity, String>,
                                          JpaSpecificationExecutor<ReviewEntity> {

  // Public list — visibility filter built-in
  Page<ReviewEntity> findByProductIdAndDeletedAtIsNullAndHiddenFalse(
      String productId, Pageable pageable);   // Pageable carries Sort

  boolean existsByProductIdAndUserIdAndDeletedAtIsNull(String productId, String userId);

  Optional<ReviewEntity> findByIdAndDeletedAtIsNull(String reviewId);

  // Recompute scope (D-08): exclude deleted + hidden
  @Query("SELECT COALESCE(AVG(r.rating), 0), COUNT(r) FROM ReviewEntity r " +
         "WHERE r.productId = :productId AND r.deletedAt IS NULL AND r.hidden = false")
  Object[] computeStats(@Param("productId") String productId);
}
```

**Lưu ý:** giữ method cũ `findByProductIdOrderByCreatedAtDesc` (callers cũ chưa migrate) HOẶC remove sau khi `ReviewService.listReviews` được port sang dùng `findByProductIdAndDeletedAtIsNullAndHiddenFalse`. Nên xoá để tránh dual code-path.

---

### `AdminReviewSpecifications.java` — CREATE (repository, query-builder)

**Không có analog trong codebase** (Specifications là pattern mới — RESEARCH §Finding 3 ASSUMED).

**Pattern verbatim (RESEARCH Finding 3):**
```java
package com.ptit.htpt.productservice.repository;

import com.ptit.htpt.productservice.domain.ReviewEntity;
import org.springframework.data.jpa.domain.Specification;

public final class AdminReviewSpecifications {
  private AdminReviewSpecifications() {}

  public static Specification<ReviewEntity> withFilter(String filter) {
    return (root, query, cb) -> switch (filter == null ? "all" : filter) {
      case "visible" -> cb.and(cb.isNull(root.get("deletedAt")), cb.isFalse(root.get("hidden")));
      case "hidden"  -> cb.isTrue(root.get("hidden"));
      case "deleted" -> cb.isNotNull(root.get("deletedAt"));
      default        -> cb.conjunction(); // "all" — không thêm predicate
    };
  }
}
```

**Quy ước package:** đặt cùng package với `ReviewRepository` để dễ import.

---

### `ReviewService.java` — MODIFY (service, CRUD + transform)

**Analog 1 — constructor + repo injection:** self lines 28-37 (giữ nguyên + thêm `@Value`).

**Analog 2 — `@Value` config (RESEARCH Finding 6):**
```java
public ReviewService(
    ReviewRepository reviewRepo,
    ProductRepository productRepo,
    RestTemplate restTemplate,
    @Value("${app.reviews.edit-window-hours:24}") long editWindowHours) {
  // ...
  this.editWindowHours = editWindowHours;
}
```

**Analog 3 — `recomputeProductRating` cô lập:** self lines 109-125 (giữ helper, đổi tham số sang `productId` rồi `findById` bên trong — RESEARCH Finding 10):
```java
private void recomputeProductRating(String productId) {
  ProductEntity product = productRepo.findById(productId).orElseThrow();
  Object[] row = reviewRepo.computeStats(productId);
  BigDecimal avg = BigDecimal.ZERO;
  int count = 0;
  if (row != null && row.length >= 2) {
    if (row[0] instanceof Number n) avg = BigDecimal.valueOf(n.doubleValue()).setScale(1, RoundingMode.HALF_UP);
    if (row[1] instanceof Number c) count = c.intValue();
  }
  product.updateRatingStats(avg, count);
  productRepo.save(product);
}
```

**Analog 4 — error envelope (Phase 13 PATTERNS Shared):**
```java
throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_EDIT_WINDOW_EXPIRED");
throw new ResponseStatusException(HttpStatus.FORBIDDEN, "REVIEW_NOT_OWNER");
throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_NOT_FOUND");
```

**Analog 5 — pagination response shape:** self lines 96-105 (giữ nguyên cho cả public list `listReviews(productId, page, size, sort)` và `listAdminReviews(page, size, filter)`).

**Phương thức mới (RESEARCH §"Approach Summary" + Finding 6):**
```java
private static Sort resolveSort(String sortKey) {
  return switch (sortKey == null ? "newest" : sortKey) {
    case "rating_desc" -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"));
    case "rating_asc"  -> Sort.by(Sort.Order.asc("rating"),  Sort.Order.desc("createdAt"));
    default            -> Sort.by(Sort.Order.desc("createdAt")); // newest + invalid → fallback
  };
}

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
  String sanitized = (content == null || content.isBlank()) ? null
      : Jsoup.clean(content, Safelist.none());
  review.applyEdit(newRating != null ? newRating : oldRating,
                   sanitized != null && sanitized.isBlank() ? null : sanitized);
  reviewRepo.save(review);
  if (newRating != null && newRating != oldRating) {
    recomputeProductRating(review.productId());   // Pitfall 2 optimization
  }
  return toResponse(review);
}

@Transactional
public void softDeleteReview(String reviewId, String userId) {
  ReviewEntity review = reviewRepo.findByIdAndDeletedAtIsNull(reviewId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND"));
  if (!review.userId().equals(userId)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "REVIEW_NOT_OWNER");
  }
  review.markDeleted();
  reviewRepo.save(review);
  recomputeProductRating(review.productId());
}

public Map<String, Object> listAdminReviews(int page, int size, String filter) {
  int safePage = Math.max(0, page);
  int safeSize = Math.min(Math.max(1, size), 100);
  Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt")));
  Page<ReviewEntity> result = reviewRepo.findAll(AdminReviewSpecifications.withFilter(filter), pageable);

  // Slug resolution per Finding 9 — batch fetch (productRepo bypasses @SQLRestriction? No;
  // documented gotcha: products đã soft-delete sẽ trả null slug — FE render "—").
  List<String> productIds = result.getContent().stream().map(ReviewEntity::productId).distinct().toList();
  Map<String, String> idToSlug = productRepo.findAllById(productIds).stream()
      .collect(Collectors.toMap(ProductEntity::id, ProductEntity::slug));
  List<AdminReviewDTO> dtos = result.getContent().stream()
      .map(r -> AdminReviewDTO.from(r, idToSlug.get(r.productId())))
      .toList();
  // build page-shape Map (như Phase 13 listReviews)
}

@Transactional
public void setVisibility(String reviewId, boolean hidden) {
  ReviewEntity review = reviewRepo.findById(reviewId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND"));
  review.setHidden(hidden);
  reviewRepo.save(review);
  recomputeProductRating(review.productId());
}

@Transactional
public void hardDelete(String reviewId) {
  ReviewEntity review = reviewRepo.findById(reviewId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND"));
  String productId = review.productId();
  reviewRepo.delete(review);
  recomputeProductRating(productId);
}
```

**Lưu ý expose `editWindowHours` cho FE:** thêm field `"config": {"editWindowHours": editWindowHours}` vào response của `listReviews` (Finding 6 ending). Service trả config; controller chỉ pass-through.

---

### `AdminReviewDTO.java` — CREATE (DTO, transform)

**Analog:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductDto.java` (record style).

**Pattern (RESEARCH Finding 9 fields):**
```java
package com.ptit.htpt.productservice.service;

import com.ptit.htpt.productservice.domain.ReviewEntity;
import java.time.Instant;

public record AdminReviewDTO(
    String id, String productId, String productSlug,
    String userId, String reviewerName,
    int rating, String content,
    boolean hidden, Instant deletedAt, Instant createdAt
) {
  public static AdminReviewDTO from(ReviewEntity r, String productSlug) {
    return new AdminReviewDTO(
        r.id(), r.productId(), productSlug,
        r.userId(), r.reviewerName(),
        r.rating(), r.content(),
        r.hidden(), r.deletedAt(), r.createdAt()
    );
  }
}
```

---

### `ReviewController.java` — MODIFY (controller, request-response)

**Analog 1 — JWT parse helper:** self lines 41-55 (giữ nguyên).

**Analog 2 — `@PatchMapping("/{id}/status")`** từ `AdminProductController.java` line 53:
```java
@PatchMapping("/{id}/status")
public ApiResponse<Object> updateStatus(@PathVariable String id, @Valid @RequestBody ProductStatusRequest request) {
  return ApiResponse.of(200, "Admin product status updated", productCrudService.updateProductStatus(id, request));
}
```

**Analog 3 — `@DeleteMapping`** từ `AdminProductController.java` line 58:
```java
@DeleteMapping("/{id}")
public ApiResponse<Map<String, Object>> deleteProduct(@PathVariable String id) {
  productCrudService.deleteProduct(id);
  return ApiResponse.of(200, "Admin product soft deleted", Map.of("id", id, "deleted", true));
}
```

**Bổ sung Phase 21 (5-15 dòng cần follow):**
```java
@GetMapping
public ApiResponse<Map<String, Object>> listReviews(
    @PathVariable String productId,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size,
    @RequestParam(defaultValue = "newest") String sort) {
  return ApiResponse.of(200, "Reviews listed",
      reviewService.listReviews(productId, page, size, sort));
}

@PatchMapping("/{reviewId}")
public ApiResponse<Map<String, Object>> editReview(
    @PathVariable String productId,
    @PathVariable String reviewId,
    @RequestHeader("Authorization") String auth,
    @Valid @RequestBody EditReviewRequest body) {
  Claims claims = parseToken(auth);
  String userId = claims.getSubject();
  return ApiResponse.of(200, "Review updated",
      reviewService.editReview(reviewId, userId, body.rating(), body.content()));
}

@DeleteMapping("/{reviewId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void softDeleteReview(
    @PathVariable String productId,
    @PathVariable String reviewId,
    @RequestHeader("Authorization") String auth) {
  Claims claims = parseToken(auth);
  String userId = claims.getSubject();
  reviewService.softDeleteReview(reviewId, userId);
}

public record EditReviewRequest(
    @Min(1) @Max(5) Integer rating,           // nullable — chỉ sửa content được
    @Size(max = 500) String content
) {}
```

---

### `AdminReviewController.java` — CREATE (controller, request-response)

**Analog 1 — request mapping + JwtRoleGuard:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/AdminProductController.java` lines 23-30 (constructor injection); `JwtRoleGuard.java` lines 34-63 (manual admin check).

**Analog 2 — `AdminStatsController` đã verify pattern `requireAdmin(authHeader)`** (RESEARCH Finding 8).

**Pattern verbatim (RESEARCH Finding 8):**
```java
package com.ptit.htpt.productservice.web;

import com.ptit.htpt.productservice.api.ApiResponse;
import com.ptit.htpt.productservice.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/products/reviews")     // gateway rewrite: /api/products/admin/reviews
public class AdminReviewController {

  private final ReviewService reviewService;
  private final JwtRoleGuard jwtRoleGuard;

  public AdminReviewController(ReviewService reviewService, JwtRoleGuard jwtRoleGuard) {
    this.reviewService = reviewService;
    this.jwtRoleGuard = jwtRoleGuard;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> list(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "all") String filter) {
    jwtRoleGuard.requireAdmin(auth);
    return ApiResponse.of(200, "Admin reviews listed",
        reviewService.listAdminReviews(page, size, filter));
  }

  @PatchMapping("/{reviewId}/visibility")
  public ApiResponse<Object> setVisibility(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
      @PathVariable String reviewId,
      @Valid @RequestBody VisibilityRequest body) {
    jwtRoleGuard.requireAdmin(auth);
    reviewService.setVisibility(reviewId, body.hidden());
    return ApiResponse.of(200, "Visibility updated", Map.of("id", reviewId, "hidden", body.hidden()));
  }

  @DeleteMapping("/{reviewId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void hardDelete(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
      @PathVariable String reviewId) {
    jwtRoleGuard.requireAdmin(auth);
    reviewService.hardDelete(reviewId);
  }

  public record VisibilityRequest(@NotNull Boolean hidden) {}
}
```

---

### `application.yml` — TOUCH (config)

**Analog:** self (`sources/backend/product-service/src/main/resources/application.yml`) — key `app.jwt.secret` đã có.

```yaml
app:
  reviews:
    edit-window-hours: 24
  jwt:
    secret: ...   # đã có sẵn — KHÔNG sửa
```

---

### `services/reviews.ts` — MODIFY (service-fe, request-response)

**Analog 1 — JSDoc header + endpoint comment:** self lines 1-7 (giữ + extend).

**Analog 2 — URLSearchParams pattern cho query string nhiều param:** `services/products.ts` lines 39-53.
```typescript
const qs = new URLSearchParams();
if (params?.page !== undefined) qs.set('page', String(params.page));
if (params?.sort)               qs.set('sort', params.sort);
const suffix = qs.toString() ? `?${qs}` : '';
return httpGet<...>(`/api/products${suffix}`);
```

**Analog 3 — admin endpoint convention** (gateway pattern Finding 1): tất cả admin URL bắt đầu `/api/products/admin/...`.

**Bổ sung Phase 21:**
```typescript
import type { Review, AdminReview, SortKey } from '@/types';
import { httpGet, httpPost, httpPatch, httpDelete } from './http';

export interface ReviewListResponse {
  content: Review[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  isFirst: boolean;
  isLast: boolean;
  config?: { editWindowHours: number };       // NEW Phase 21 (Finding 6)
}

export function listReviews(productId: string, page = 0, size = 10, sort: SortKey = 'newest'): Promise<ReviewListResponse> {
  const qs = new URLSearchParams();
  qs.set('page', String(page));
  qs.set('size', String(size));
  if (sort !== 'newest') qs.set('sort', sort);
  return httpGet<ReviewListResponse>(
    `/api/products/${encodeURIComponent(productId)}/reviews?${qs}`
  );
}

export function editReview(productId: string, reviewId: string, body: { rating?: number; content?: string }): Promise<Review> {
  return httpPatch<Review>(
    `/api/products/${encodeURIComponent(productId)}/reviews/${encodeURIComponent(reviewId)}`,
    body
  );
}

export function softDeleteReview(productId: string, reviewId: string): Promise<void> {
  return httpDelete<void>(
    `/api/products/${encodeURIComponent(productId)}/reviews/${encodeURIComponent(reviewId)}`
  );
}

// === Admin endpoints (Finding 1: gateway rewrite /api/products/admin/** → /admin/products/**) ===

export interface AdminReviewListResponse {
  content: AdminReview[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  isFirst: boolean;
  isLast: boolean;
}

export function listAdminReviews(page = 0, size = 20, filter: 'all'|'visible'|'hidden'|'deleted' = 'all'): Promise<AdminReviewListResponse> {
  const qs = new URLSearchParams({ page: String(page), size: String(size), filter });
  return httpGet<AdminReviewListResponse>(`/api/products/admin/reviews?${qs}`);
}

export function setReviewVisibility(reviewId: string, hidden: boolean): Promise<void> {
  return httpPatch<void>(
    `/api/products/admin/reviews/${encodeURIComponent(reviewId)}/visibility`,
    { hidden }
  );
}

export function hardDeleteReview(reviewId: string): Promise<void> {
  return httpDelete<void>(
    `/api/products/admin/reviews/${encodeURIComponent(reviewId)}`
  );
}
```

---

### `types/index.ts` — MODIFY (utility)

**Analog:** self.

**Bổ sung verbatim:**
```typescript
export interface Review {
  id: string;
  userId: string;
  reviewerName: string;
  productId: string;
  rating: number;
  content?: string;
  createdAt: string;
  hidden?: boolean;              // NEW Phase 21 (admin context)
  deletedAt?: string | null;     // NEW Phase 21
}

export type SortKey = 'newest' | 'rating_desc' | 'rating_asc';

export interface AdminReview extends Review {
  productSlug: string | null;     // null khi product đã soft-delete (Finding 9 gotcha)
  productName?: string;
}
```

---

### `app/products/[slug]/ReviewSection/ReviewSection.tsx` — MODIFY (component, orchestrator)

**Analog:** self lines 1-122 (giữ skeleton).

**Pattern thêm sort state + URL persist (RESEARCH Finding 7):**
```typescript
'use client';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import type { SortKey } from '@/types';

const pathname = usePathname();
const router = useRouter();
const searchParams = useSearchParams();
const initialSort = (searchParams.get('sort') as SortKey) ?? 'newest';
const [sort, setSort] = useState<SortKey>(initialSort);

const onSortChange = (newSort: SortKey) => {
  const params = new URLSearchParams(searchParams.toString());
  if (newSort === 'newest') params.delete('sort');
  else params.set('sort', newSort);
  const qs = params.toString();
  router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  setSort(newSort);
  loadPage(0, false, newSort);   // refetch page 0
};
```

**Pattern pass `currentUserId` + `editWindowHours` xuống ReviewList:**
```typescript
const { user } = useAuth();
// editWindowHours đọc từ res.config (Finding 6)
const [editWindowHours, setEditWindowHours] = useState(24);

// Trong loadPage success:
if (res.config?.editWindowHours) setEditWindowHours(res.config.editWindowHours);

<ReviewList
  reviews={reviews}
  currentUserId={user?.id}              // Pitfall 5: field `id` đã verify
  editWindowHours={editWindowHours}
  sort={sort}
  onSortChange={onSortChange}
  onEdit={handleEdit}
  onDelete={handleDelete}
  /* ... existing props ... */
/>
```

**Edit/Delete handlers (full refetch — discretion D-23):**
```typescript
const handleEdit = useCallback(async (reviewId: string, body: { rating?: number; content?: string }) => {
  try {
    await editReview(productId, reviewId, body);
    showToast('Đã cập nhật đánh giá', 'success');
    await loadPage(0, false, sort);
  } catch (err) {
    if (isApiError(err)) {
      if (err.code === 'REVIEW_EDIT_WINDOW_EXPIRED') {
        showToast('Đã quá thời hạn chỉnh sửa (24h kể từ lúc đăng)', 'error'); return;
      }
      if (err.code === 'REVIEW_NOT_OWNER') {
        showToast('Bạn không có quyền chỉnh sửa review này', 'error'); return;
      }
      if (err.code === 'REVIEW_NOT_FOUND') {
        showToast('Review không tồn tại hoặc đã bị xoá', 'error'); return;
      }
    }
    showToast('Đã xảy ra lỗi. Vui lòng thử lại.', 'error');
    throw err;
  }
}, [productId, sort, showToast, loadPage]);

const handleDelete = useCallback(async (reviewId: string) => {
  if (!window.confirm('Xoá đánh giá này? Hành động không thể hoàn tác.')) return;
  try {
    await softDeleteReview(productId, reviewId);
    showToast('Đã xoá đánh giá', 'success');
    await loadPage(0, false, sort);
  } catch {
    showToast('Không thể xoá đánh giá. Vui lòng thử lại.', 'error');
  }
}, [productId, sort, showToast, loadPage]);
```

---

### `ReviewList.tsx` — MODIFY (component, presenter + sort + actions)

**Analog 1 — sort dropdown header:** native `<select>` style (admin/products page line 313-329 dùng pattern tương tự).

**Analog 2 — list render + per-item layout:** self lines 71-91.

**Bổ sung props + sort dropdown header:**
```typescript
interface ReviewListProps {
  /* ... existing ... */
  currentUserId?: string;
  editWindowHours: number;
  sort: SortKey;
  onSortChange: (s: SortKey) => void;
  onEdit: (reviewId: string, body: { rating?: number; content?: string }) => Promise<void>;
  onDelete: (reviewId: string) => Promise<void>;
}

// Trong return, thay <h3> bằng row chứa <h3> + <select>:
<div className={styles.listHeader}>
  <h3 className={styles.listHeading}>Đánh giá từ khách hàng ({totalElements})</h3>
  <select
    className={styles.sortDropdown}
    value={sort}
    onChange={(e) => onSortChange(e.target.value as SortKey)}
    aria-label="Sắp xếp đánh giá"
  >
    <option value="newest">Mới nhất</option>
    <option value="rating_desc">Đánh giá cao nhất</option>
    <option value="rating_asc">Đánh giá thấp nhất</option>
  </select>
</div>
```

**Per-item edit/delete actions (D-21):**
```typescript
const isOwner = currentUserId && review.userId === currentUserId;
const editExpired = (Date.now() - new Date(review.createdAt).getTime()) > editWindowHours * 3600 * 1000;
const [editingId, setEditingId] = useState<string | null>(null);

// Trong <li>:
{isOwner && editingId !== review.id && (
  <div className={styles.actionsRow}>
    <button
      type="button"
      className={styles.actionLink}
      disabled={editExpired}
      title={editExpired ? 'Đã quá thời hạn chỉnh sửa (24h)' : undefined}
      onClick={() => setEditingId(review.id)}
    >Sửa</button>
    <button
      type="button"
      className={`${styles.actionLink} ${styles.actionDanger}`}
      onClick={() => onDelete(review.id)}
    >Xoá</button>
  </div>
)}

{isOwner && editingId === review.id && (
  <ReviewForm
    mode="edit"
    initialValues={{ rating: review.rating, content: review.content ?? '' }}
    onCancel={() => setEditingId(null)}
    onSubmit={async (data) => {
      await onEdit(review.id, data);
      setEditingId(null);
    }}
  />
)}
```

---

### `ReviewForm.tsx` — MODIFY (component, rhf form)

**Analog:** self lines 1-86.

**Bổ sung props (D-22):**
```typescript
interface ReviewFormProps {
  mode?: 'create' | 'edit';
  initialValues?: { rating: number; content?: string };
  onSubmit: (data: { rating: number; content?: string }) => Promise<void>;
  onCancel?: () => void;       // chỉ render nút Huỷ khi mode='edit'
}

export default function ReviewForm({ mode = 'create', initialValues, onSubmit, onCancel }: ReviewFormProps) {
  const { register, handleSubmit, setValue, watch, reset, formState: { errors } } = useForm<ReviewFormData>({
    resolver: zodResolver(reviewSchema),
    defaultValues: {
      rating: initialValues?.rating ?? 0,
      content: initialValues?.content ?? '',
    },
  });

  // Pitfall 9 (RESEARCH): reset khi mode hoặc initialValues đổi
  useEffect(() => {
    reset({ rating: initialValues?.rating ?? 0, content: initialValues?.content ?? '' });
  }, [mode, initialValues, reset]);

  // Trong submit row:
  <div className={styles.submitRow}>
    {mode === 'edit' && onCancel && (
      <Button type="button" variant="secondary" onClick={onCancel} disabled={submitting}>Huỷ</Button>
    )}
    <Button type="submit" variant="primary" loading={submitting} disabled={submitting}>
      {mode === 'edit' ? 'Lưu thay đổi' : 'Gửi đánh giá'}
    </Button>
  </div>
```

**Lưu ý:** giữ `reset({ rating: 0, content: '' })` cho `mode='create'` sau submit; trong `mode='edit'` không reset (cha unmount form qua `setEditingId(null)`).

---

### `ReviewSection.module.css` — MODIFY (styles)

**Analog:** self.

**Cần thêm classes (5-15 dòng):**
```css
.listHeader {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}
.sortDropdown {
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  border: 1.5px solid var(--outline-variant);
  background: var(--surface-container-lowest);
  font-family: var(--font-family-body);
  cursor: pointer;
}
.actionsRow {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-2);
}
.actionLink {
  background: none;
  border: none;
  padding: 0;
  font-size: var(--text-body-sm);
  color: var(--primary);
  cursor: pointer;
}
.actionLink:disabled { color: var(--on-surface-variant); cursor: not-allowed; }
.actionDanger { color: var(--error); }
```

---

### `app/admin/reviews/page.tsx` — CREATE (page, admin table)

**Analog (exact, 1-1 clone):** `sources/frontend/src/app/admin/products/page.tsx` lines 1-385.

**Patterns to copy (clone-then-adapt):**

**Imports + state (lines 1-58):**
```typescript
'use client';
import React, { useCallback, useEffect, useState } from 'react';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { useToast } from '@/components/ui/Toast/Toast';
import { listAdminReviews, setReviewVisibility, hardDeleteReview } from '@/services/reviews';
import type { AdminReview } from '@/types';
```

**Pagination + filter state + load callback (lines 46-74 của analog):**
```typescript
const [page, setPage] = useState(0);
const [size] = useState(20);
const [filter, setFilter] = useState<'all'|'visible'|'hidden'|'deleted'>('all');
const [reviews, setReviews] = useState<AdminReview[]>([]);
const [meta, setMeta] = useState<{ totalElements: number; totalPages: number; isLast: boolean } | null>(null);
const [loading, setLoading] = useState(false);
const [failed, setFailed] = useState(false);
const { showToast } = useToast();

const load = useCallback(async () => {
  setLoading(true); setFailed(false);
  try {
    const resp = await listAdminReviews(page, size, filter);
    setReviews(resp.content);
    setMeta({ totalElements: resp.totalElements, totalPages: resp.totalPages, isLast: resp.isLast });
  } catch { setFailed(true); }
  finally { setLoading(false); }
}, [page, size, filter]);

useEffect(() => { load(); }, [load]);
```

**Filter dropdown (clone style từ analog category select lines 313-329):**
```typescript
<select
  value={filter}
  onChange={(e) => { setPage(0); setFilter(e.target.value as typeof filter); }}
  style={{ padding: 'var(--space-3)', borderRadius: 'var(--radius-lg)', border: '1.5px solid rgba(195,198,214,0.2)', background: 'var(--surface-container-lowest)' }}
>
  <option value="all">Tất cả</option>
  <option value="visible">Đang hiện</option>
  <option value="hidden">Đã ẩn</option>
  <option value="deleted">Đã xoá (author)</option>
</select>
```

**Status badge mapping (D-14 / specifics line 239 — wording tiếng Việt):**
```typescript
function statusBadge(r: AdminReview) {
  if (r.deletedAt) return <Badge variant="default">Đã xoá</Badge>;
  if (r.hidden)    return <Badge variant="out-of-stock">Ẩn</Badge>;
  return <Badge variant="new">Hiện</Badge>;
}
```

**Action buttons inline + window.confirm (D-17):**
```typescript
async function onToggleHidden(r: AdminReview) {
  try {
    await setReviewVisibility(r.id, !r.hidden);
    showToast(r.hidden ? 'Đã bỏ ẩn review' : 'Đã ẩn review', 'success');
    await load();
  } catch { showToast('Không thể cập nhật trạng thái', 'error'); }
}
async function onHardDelete(r: AdminReview) {
  if (!window.confirm('Xoá vĩnh viễn review này? Không thể hoàn tác.')) return;
  try {
    await hardDeleteReview(r.id);
    showToast('Đã xoá vĩnh viễn', 'success');
    await load();
  } catch { showToast('Không thể xoá review', 'error'); }
}

// Trong cell action:
<div className={styles.actions}>
  {!r.deletedAt && (
    <button className={styles.actionBtn} onClick={() => onToggleHidden(r)}>
      {r.hidden ? 'Bỏ ẩn' : 'Ẩn'}
    </button>
  )}
  <button className={`${styles.actionBtn} ${styles.deleteBtn}`} onClick={() => onHardDelete(r)}>Xoá</button>
</div>
```

**Cột Sản phẩm (truncate + link):**
```typescript
<td>
  {r.productSlug ? (
    <Link href={`/products/${r.productSlug}`}>{r.productName ?? r.productSlug}</Link>
  ) : <span>—</span>}
</td>
```

**Pagination footer (FE chưa có dedicated `Pagination` component — render inline):**
```typescript
{meta && meta.totalPages > 1 && (
  <div className={styles.paginationRow}>
    <Button variant="secondary" disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}>Trước</Button>
    <span>Trang {page + 1} / {meta.totalPages}</span>
    <Button variant="secondary" disabled={meta.isLast} onClick={() => setPage(p => p + 1)}>Sau</Button>
  </div>
)}
```

---

### `app/admin/reviews/page.module.css` — CREATE (styles)

**Analog (clone exact):** `sources/frontend/src/app/admin/products/page.module.css` (toàn bộ — copy + adapt class names cho table reviews).

---

### `app/admin/layout.tsx` — TOUCH (layout)

**Analog:** self lines 11-16 (`navItems` array).

**Insert vào `navItems` (giữa Đơn hàng và Tài khoản):**
```typescript
{ href: '/admin/orders', label: 'Đơn hàng', icon: <svg .../> },
{ href: '/admin/reviews', label: 'Đánh giá', icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg> },
{ href: '/admin/users', label: 'Tài khoản', icon: <svg .../> },
```

---

### Test files

#### `ReviewServiceTest.java` — MODIFY

**Analog:** self (Phase 13 baseline).

**New tests (RESEARCH §"Phase Requirements → Test Map"):**
- `editReview_ownerWithinWindow_returns200`
- `editReview_nonOwner_returns403` (`REVIEW_NOT_OWNER`)
- `editReview_pastWindow_returns422` — set `editWindowHours=0` qua `@TestPropertySource(properties = "app.reviews.edit-window-hours=0")`
- `editReview_softDeleted_returns422` (`REVIEW_NOT_FOUND`)
- `editReview_contentOnly_skipsRecompute` — verify `productRepo.save` count=0 hoặc product.updatedAt giữ nguyên
- `softDelete_owner_returns204AndRecomputes`
- `reReview_afterSoftDelete_succeeds` — partial UNIQUE
- `setVisibility_hide_recomputesAvg` / `unhide_recomputesAvg`
- `hardDelete_removesRow_recomputes`
- `recompute_excludesHiddenAndDeleted`
- `recompute_resetsToZero_whenAllReviewsDeleted`

#### `ReviewControllerTest.java` — MODIFY

**Analog:** self (MockMvc setup từ Phase 13).

**New MockMvc cases:**
- `PATCH /reviews/{id}` 200 (owner + valid + within window)
- 403 non-owner
- 422 `REVIEW_EDIT_WINDOW_EXPIRED` / `REVIEW_NOT_FOUND`
- `DELETE /reviews/{id}` 204 owner / 403 non-owner
- `GET /reviews?sort=rating_desc` order verify
- `GET /reviews?sort=invalid` fallback newest (200, không 400)

#### `AdminReviewControllerTest.java` — CREATE

**Analog:** `ReviewControllerTest.java` MockMvc setup + `JwtRoleGuard` test pattern.

**Cases:**
- 401 missing Authorization
- 403 token không có `roles=ADMIN`
- 200 list filter all/visible/hidden/deleted
- PATCH visibility + recompute
- DELETE 204 + row gone

---

## Shared Patterns

### ApiResponse Envelope (BE)
**Source:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/api/ApiResponse.java`
**Apply to:** `ReviewController.java` (modify), `AdminReviewController.java` (create)

```java
return ApiResponse.of(200, "Reviews listed", data);
return ApiResponse.of(200, "Review updated", data);
return ApiResponse.of(200, "Visibility updated", Map.of("id", reviewId, "hidden", hidden));
// 204 no-content endpoints: return void với @ResponseStatus(HttpStatus.NO_CONTENT)
```

### ResponseStatusException Error Codes (BE)
**Source:** Phase 13 PATTERNS §"ResponseStatusException Error Handling"
**Apply to:** `ReviewService.java` (5 methods mới)

```java
throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_NOT_FOUND");
throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_EDIT_WINDOW_EXPIRED");
throw new ResponseStatusException(HttpStatus.FORBIDDEN, "REVIEW_NOT_OWNER");
throw new ResponseStatusException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND");      // hard-delete chưa-tồn-tại
```

`GlobalExceptionHandler` tự catch và trả `ApiErrorResponse { code, message, status, ... }`.

### JWT Bearer Parse (BE)
**Source:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ReviewController.java` lines 41-55 (`parseToken`) + `JwtRoleGuard.java` lines 34-63 (`requireAdmin`).
**Apply to:**
- `ReviewController.editReview` / `softDeleteReview`: dùng `parseToken(auth)` + `claims.getSubject()` (đã có).
- `AdminReviewController.*`: dùng `jwtRoleGuard.requireAdmin(auth)` (manual admin check).

### Recompute helper invariant (BE)
**Source:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ReviewService.java` lines 109-125 (Phase 13).
**Apply to:** ALL 5 mutation paths Phase 21 (Pitfall 1).

Cô lập thành `recomputeProductRating(String productId)` — gọi cuối mỗi method:
- `editReview` — chỉ gọi nếu `newRating != oldRating` (Pitfall 2 optimization)
- `softDeleteReview`, `setVisibility`, `hardDelete` — luôn gọi.

### `httpPatch` / `httpDelete` (FE)
**Source:** `sources/frontend/src/services/http.ts` lines 144-148 (đã có sẵn từ trước, không cần thêm).
**Apply to:** `services/reviews.ts` (5 endpoint mới).

```typescript
import { httpGet, httpPost, httpPatch, httpDelete } from './http';
```

### `useToast` hook (FE)
**Source:** Phase 13 PATTERNS §"useToast Hook".
**Apply to:** `ReviewSection.tsx` (toast wording theo CONTEXT specifics line 240); `app/admin/reviews/page.tsx`.

```typescript
showToast('Đã cập nhật đánh giá', 'success');
showToast('Đã xoá đánh giá', 'success');
showToast('Đã ẩn review', 'success');
showToast('Đã bỏ ẩn review', 'success');
showToast('Đã xoá vĩnh viễn', 'success');
```

### `isApiError` + error code mapping (FE)
**Source:** `sources/frontend/src/services/errors.ts` (Phase 13 đã dùng).
**Apply to:** `ReviewSection.handleEdit`.

```typescript
if (isApiError(err) && err.code === 'REVIEW_EDIT_WINDOW_EXPIRED') {
  showToast('Đã quá thời hạn chỉnh sửa (24h kể từ lúc đăng)', 'error');
}
// (CONTEXT specifics lines 242-244 — wording chốt)
```

### `useAuth` field convention (FE)
**Source:** `sources/frontend/src/providers/AuthProvider.tsx` line 18 — `user: { id: string; email: string; name: string } | null` (RESEARCH Pitfall 5 verified).
**Apply to:** `ReviewSection.tsx` (pass `user?.id` xuống ReviewList).

### Admin sidebar nav pattern (FE)
**Source:** `app/admin/layout.tsx` lines 11-16 (`navItems` array với inline SVG icon).
**Apply to:** TOUCH thêm 1 entry.

### URL persistence sort (FE)
**Source:** RESEARCH Finding 7 — `usePathname` + `useSearchParams` + `router.replace(qs, { scroll: false })`.
**Apply to:** `ReviewSection.tsx`. Default `newest` không ghi vào URL (D-13).

---

## No Analog Found

| File | Role | Data Flow | Lý do |
|------|------|-----------|-------|
| `AdminReviewSpecifications.java` | repository (Specification factory) | query-builder | Codebase chưa dùng JPA Specifications. Pattern verbatim từ RESEARCH Finding 3 (đã CITED `docs.spring.io/spring-data-jpa/reference/jpa/specifications.html`). |
| `application-test.yml` (override `app.reviews.edit-window-hours=0`) | test config | — | Codebase chưa có test profile riêng — chỉ TOUCH thêm 1 key. Có thể thay bằng `@TestPropertySource(properties = "app.reviews.edit-window-hours=0")` annotation trên test class (không cần file mới). |

---

## Cross-cutting Map (REQ → endpoints → service → repo)

| REQ | Endpoint | Controller method | Service method | Repository call | Recompute? |
|-----|----------|-------------------|----------------|-----------------|------------|
| REV-04 edit | `PATCH /api/products/{pid}/reviews/{rid}` | `ReviewController.editReview` | `ReviewService.editReview` | `findByIdAndDeletedAtIsNull` + `save` | only if rating changed |
| REV-04 delete | `DELETE /api/products/{pid}/reviews/{rid}` | `ReviewController.softDeleteReview` | `ReviewService.softDeleteReview` | `findByIdAndDeletedAtIsNull` + `save` (markDeleted) | YES |
| REV-04 re-review | `POST /api/products/{pid}/reviews` (existing) | `createReview` (existing) | `createReview` (existing) | `existsByProductIdAndUserIdAndDeletedAtIsNull` (NEW — replace cũ) | YES |
| REV-05 sort | `GET /api/products/{pid}/reviews?sort=...` | `ReviewController.listReviews` (extend) | `ReviewService.listReviews(productId, page, size, sort)` | `findByProductIdAndDeletedAtIsNullAndHiddenFalse(productId, Pageable)` | — |
| REV-06 admin list | `GET /api/products/admin/reviews?filter=...` | `AdminReviewController.list` | `ReviewService.listAdminReviews` | `reviewRepo.findAll(spec, pageable)` + `productRepo.findAllById(ids)` | — |
| REV-06 hide/unhide | `PATCH /api/products/admin/reviews/{rid}/visibility` | `AdminReviewController.setVisibility` | `ReviewService.setVisibility` | `findById` + `save` (setHidden) | YES |
| REV-06 hard-delete | `DELETE /api/products/admin/reviews/{rid}` | `AdminReviewController.hardDelete` | `ReviewService.hardDelete` | `findById` + `delete` | YES |

**DB columns/indexes touched (V7):**
- `reviews.deleted_at` (NEW, nullable timestamptz) — soft-delete marker
- `reviews.hidden` (NEW, NOT NULL DEFAULT FALSE) — moderation flag
- `uq_review_product_user` (DROP) → `uq_review_product_user_active` (CREATE, partial WHERE deleted_at IS NULL)
- `idx_reviews_visibility` (NEW, `(product_id, hidden, deleted_at)`)

---

## Metadata

**Analog search scope:** `sources/backend/product-service/`, `sources/backend/api-gateway/src/main/resources/application.yml`, `sources/frontend/src/{services,app/admin,app/products,types,providers}`.
**Files inspected:** 11 BE + 8 FE = 19 files (full reads on small files; targeted reads on large pages).
**Pattern extraction date:** 2026-05-02
**Phase 13 PATTERNS reused:** ApiResponse envelope, ResponseStatusException error code convention, JWT parseToken pattern, recompute helper invariant, useToast/isApiError/httpGet helpers — Phase 21 EXTENDS these contracts (no breaking changes).

## PATTERN MAPPING COMPLETE
