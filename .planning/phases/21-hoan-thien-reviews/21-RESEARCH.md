# Phase 21: Hoàn Thiện Reviews — Research

**Researched:** 2026-05-02
**Domain:** Author edit/delete reviews + sort + admin moderation (mở rộng nền Phase 13)
**Confidence:** HIGH (toàn bộ findings grounded trên codebase đã đọc trực tiếp + locked decisions từ CONTEXT.md)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

> Tham chiếu đầy đủ: `.planning/phases/21-hoan-thien-reviews/21-CONTEXT.md` (D-01..D-27).
> Phần dưới copy verbatim các nhóm chính — **planner KHÔNG được lật lại các locked decisions này**.

### Locked Decisions (rút gọn theo nhóm — chi tiết xem CONTEXT.md)

**A. Author edit/delete (REV-04)**
- D-01..D-02: Edit window = 24h kể từ `created_at`, configurable qua `app.reviews.edit-window-hours: 24` (Spring `@Value`); FE đọc giá trị từ BE (không hard-code).
- D-03: Author delete = soft-delete `deleted_at = now()`, không có time window, trả 204.
- D-04: `PATCH /api/products/{productId}/reviews/{reviewId}` body `{ rating?, content? }`. Auth Bearer. 403 nếu không phải owner. 422 `REVIEW_EDIT_WINDOW_EXPIRED` nếu past 24h. 422 `REVIEW_NOT_FOUND` nếu đã soft-delete. Sanitize Jsoup. Sửa rating → recompute avg.
- D-05: `DELETE /api/products/{productId}/reviews/{reviewId}` — owner only, set `deleted_at`, recompute. Idempotent — review đã deleted → 404.
- D-06: Sau soft-delete, author CAN re-review. UNIQUE constraint cũ block redo → migrate sang **partial unique index** `WHERE deleted_at IS NULL` (D-20).

**B. Visibility & avg_rating recompute**
- D-07: Public list filter `deleted_at IS NULL AND hidden = FALSE` (cả query lẫn count).
- D-08: `avg_rating` + `review_count` recompute **loại bỏ cả deleted lẫn hidden**. Trigger ở 5 mutation path mới: edit-rating-changed / author-delete / admin-hide / admin-unhide / admin-hard-delete. `computeStats` JPQL update WHERE.
- D-09: Author không thấy review đã soft-delete hoặc hidden của chính mình trong public list (transparent moderation).
- D-10: Admin list KHÔNG lọc hidden, KHÔNG lọc deleted_at. Filter UI: `Tất cả | Đang hiện | Đã ẩn | Đã xoá (author)`. Default `Tất cả`.

**C. Sort UX & API (REV-05)**
- D-11..D-12: Sort options FE: `Mới nhất` (default `newest`) / `Đánh giá cao nhất` (`rating_desc`) / `Đánh giá thấp nhất` (`rating_asc`). API `?sort=newest|rating_desc|rating_asc`. Tie-break: `created_at DESC`. Invalid → fallback `newest` (KHÔNG throw 400).
- D-13: FE control = native `<select>` đặt cạnh `<h3>` "Đánh giá từ khách hàng (N)" trong `ReviewList.tsx`. Đổi sort → fetch page 0 + reset list. URL persistence qua `router.replace()` (không scroll, không reload). Default sort không cần ghi vào URL.

**D. Admin moderation UI (REV-06)**
- D-14..D-15: Page `/admin/reviews/page.tsx` — table layout match `/admin/products`. Columns: Sản phẩm (link `/products/{slug}`) | Reviewer | Rating (★) | Trích đoạn content (truncate 60 ký tự + tooltip) | Trạng thái badge | Ngày tạo | Hành động. Filter dropdown 4-state. KHÔNG keyword search.
- D-16: Pagination server-side `?page=&size=20`.
- D-17: Actions inline: Ẩn/Bỏ ẩn (PATCH visibility) + Xoá vĩnh viễn (DELETE → hard delete) với `window.confirm`. Row đã `deleted_at IS NOT NULL` chỉ hiện nút "Xoá".
- D-18..D-19: Admin endpoints: `GET /admin/products/reviews`, `PATCH /admin/products/reviews/{id}/visibility`, `DELETE /admin/products/reviews/{id}`. Authorization: existing `JwtRoleGuard.requireAdmin()` (manual check, KHÔNG @PreAuthorize). **Lưu ý URL path:** xem Finding 1 — gateway hiện KHÔNG có `/api/admin/**`, phải dùng `/api/products/admin/reviews/**` rewrite → `/admin/products/reviews/**`.

**E. Database migration (V7)**
- D-20: V7 single migration: ADD `deleted_at TIMESTAMPTZ NULL` + `hidden BOOLEAN NOT NULL DEFAULT FALSE`; DROP CONSTRAINT `uq_review_product_user`; CREATE UNIQUE INDEX partial `WHERE deleted_at IS NULL`; CREATE INDEX `idx_reviews_visibility (product_id, hidden, deleted_at)`. ReviewEntity thêm fields + mutators `markDeleted()` / `setHidden(boolean)` / `applyEdit(rating, content)`.

**F. Frontend ReviewList changes**
- D-21..D-23: ReviewList nhận thêm `currentUserId?: string` + `editWindowHours: number`. Owner thấy "Sửa | Xoá". Edit inline qua `ReviewForm mode="edit"`. Delete = `window.confirm` → DELETE → toast + refetch. Edit window expired → button disabled + tooltip.

**G. Service/API surface**
- D-24..D-27: Mở rộng `ReviewController` (PATCH/DELETE author endpoints). Tạo mới `AdminReviewController` (`/admin/products/reviews`). Mở rộng `ReviewService` (editReview / softDeleteReview / listAdminReviews / setVisibility / hardDelete). Repository: dùng JPA `Sort` parameter — single method `findVisibleByProductId(productId, Pageable)` cho public; admin dùng Specification hoặc multiple finders. `computeStats` thêm `r.deletedAt IS NULL AND r.hidden = false` vào WHERE.

### Claude's Discretion
- CSS layout chính xác cho action buttons "Sửa | Xoá" trong review item (hài hoà với `ReviewSection.module.css`).
- Confirmation dialog component: dùng custom `ConfirmDialog` nếu có sẵn, fallback `window.confirm` — phase này chấp nhận `window.confirm`.
- Resolve `productSlug` cho admin list: BE join SQL hay batch fetch FE — chọn approach đơn giản hơn sau khi inspect `ProductRepository`.
- Optimistic update vs full refetch: chọn full refetch (đơn giản, ít edge case).
- Toast wording chính xác (giữ tone Vietnamese hiện có).
- Verify `ProductEntity.updateRatingStats` đã handle count = 0 (avg_rating reset về 0); fix nếu chưa.

### Deferred Ideas (OUT OF SCOPE)
- REV-05 helpful sort (defer v1.4 — cần `helpful_count` column).
- Admin keyword search trên `/admin/reviews`.
- Bulk moderation actions (multi-select hide/delete).
- Audit log admin moderation actions (`review_moderation_log` table).
- Edit history / revisions.
- Email notify reviewer khi bị hide.
- Rate limit author edit.
- i18n cho admin moderation page (Vietnamese only).
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **REV-04** | Author edit/delete review của mình. Edit chỉ chủ review hoặc 24h sau publish (configurable). Delete = soft-delete (`deleted_at` column), avg_rating recalc loại bỏ deleted. Admin vẫn xem. | V7 migration spec (Finding 5), edit-window pattern (Finding 6), Jsoup reuse (Phase 13 D-14), partial UNIQUE index pattern (Finding 4). |
| **REV-05** | Sort review list by `helpful` (defer fallback `created_at DESC`) / `newest` / `rating DESC` / `rating ASC`. Dropdown FE + BE query param. | JPA `Sort` switch pattern (Finding 2), Next.js `router.replace()` URL state (Finding 7). |
| **REV-06** | Admin moderation: `/admin/reviews` screen list + filter (visible/hidden) + actions hide/unhide/delete. Hide = `hidden BOOLEAN` column. | AdminReviewController pattern + JwtRoleGuard.requireAdmin (Finding 8), gateway rewrite `/api/products/admin/reviews/**` (Finding 1), Specification API for filter (Finding 3). |
</phase_requirements>

---

## Summary

Phase 21 hoàn thiện vòng đời review (sửa, xoá, sort, kiểm duyệt) trên nền móng Phase 13. Toàn bộ kiến trúc cốt lõi đã có sẵn — phase này thuần mở rộng:
- 1 schema migration mới (`V7__add_review_moderation_columns.sql`).
- 5 endpoint mới (PATCH author edit, DELETE author soft-delete, GET admin list, PATCH visibility, DELETE hard-delete).
- 1 page mới (`/admin/reviews`), 2 component thay đổi đáng kể (`ReviewList`, `ReviewForm`), 1 sort dropdown.

**3 phát hiện quan trọng nhất:**

1. **Gateway routing convention KHÔNG có `/api/admin/**`.** CONTEXT.md D-18 viết "route qua gateway `/api/admin/**` đã có (admin products/orders/users)" — đây là sai sót về fact. Pattern thực tế: `/api/{service}/admin/**` → rewrite → `/admin/{service}/**`. AdminProductController dùng `@RequestMapping("/admin/products")`, AdminStatsController dùng `@RequestMapping("/admin/products")` + `/stats`. **AdminReviewController phải dùng `@RequestMapping("/admin/products/reviews")`** và FE gọi `/api/products/admin/reviews/...`. Không cần thêm route mới vào gateway — route `product-service-admin` (`/api/products/admin/**`) đã cover.

2. **`@SQLRestriction` không phù hợp cho ReviewEntity** (admin cần thấy soft-deleted). Lọc visibility ở **service/repository layer** thông qua: (a) finder method names cho public list (`findByProductIdAndDeletedAtIsNullAndHiddenFalse...`), (b) JPA Specifications cho admin list 4-state filter. `computeStats` JPQL phải thêm `r.deletedAt IS NULL AND r.hidden = false`.

3. **Recompute path expansion = risk hotspot.** Phase 13 chỉ recompute ở 1 path (POST review). Phase 21 thêm 5 path mới. Risk: quên 1 path → drift `avg_rating`. Mitigation: (a) Test integration kiểm tra avg sau mỗi mutation, (b) cô lập helper `recomputeProductRating(productId)` ở service layer và gọi từ mọi path, (c) Edit endpoint có optimization: nếu rating KHÔNG đổi → skip recompute.

**Primary recommendation:** Triển khai theo thứ tự sau (không có new external dep, mọi library đều đã trong stack):
1. Wave 0: V7 migration + ReviewEntity mutators + Spring `@Value` config + `editWindowHours` exposed qua endpoint hoặc embed vào response.
2. Wave 1 BE: Repository (sort-aware finder + visibility-filtered finders + Specification) + Service mutations + Controllers (PATCH/DELETE author + AdminReviewController) + tests.
3. Wave 2 FE: Service layer (`reviews.ts` mở rộng) + types + `ReviewList` sort dropdown + `ReviewForm` edit mode + `/admin/reviews/page.tsx` + sidebar link + admin layout nav update.
4. Wave 3 E2E: Playwright spec author edit/delete + admin moderation flow.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Edit window enforcement (24h) | API / Backend (product-svc) | Browser / Client (advisory only) | BE re-check authoritative — D-04; FE `editWindowHours` chỉ để khoá button UX |
| Author authorization (owner check) | API / Backend (product-svc ReviewController) | — | JWT sub claim → ownership compare; FE state không trust-able |
| Soft-delete vs hard-delete | API / Backend (product-svc ReviewService) | Database | Service layer set `deleted_at` (author) hoặc `repository.delete()` (admin) |
| Visibility filter (public list) | API / Backend (product-svc ReviewRepository) | — | Finder `...AndDeletedAtIsNullAndHiddenFalse...` ở repository layer |
| Admin list filter 4-state | API / Backend (product-svc ReviewService) | Browser / Client | JPA Specification build dynamic predicate; FE chỉ pass `filter=` query |
| Sort `?sort=` mapping | API / Backend (Controller → Sort object) | — | Switch case nhỏ trong Controller hoặc Service; pass Pageable.of(page, size, sort) |
| URL persistence sort | Browser / Client (FE) | — | `router.replace()` + `useSearchParams` |
| Admin role check | API / Backend (`JwtRoleGuard.requireAdmin`) | — | Manual JWT check (codebase chưa Spring Security) |
| Recompute avg_rating | API / Backend (product-svc ReviewService) | Database | `recomputeProductRating(productId)` helper, gọi ở 5 path mutation, cùng `@Transactional` |
| Author edit/delete buttons | Browser / Client (FE ReviewList) | — | Render conditionally khi `review.userId === currentUserId` |
| Admin moderation page | Browser / Client (FE `/admin/reviews/page.tsx`) | API / Backend | Reuse table+filter+pagination pattern từ `/admin/products` |
| Slug resolution cho admin row | API / Backend (BE join) hoặc Browser / Client (batch fetch) | — | Discretion D-25; recommend BE JOIN ở Service.toAdminResponse() (Finding 9) |

---

## Standard Stack

### Core (đã có sẵn — không thêm dep mới)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.3.2 | Framework | [VERIFIED: Phase 13 RESEARCH] |
| Spring Data JPA | (BOM) | `Sort` object + `Pageable` + `Specification` | [VERIFIED: ReviewRepository hiện đang dùng Pageable] |
| JJWT | 0.12.7 | JWT parse cho ownership check | [VERIFIED: ReviewController.parseToken hiện tại + JwtRoleGuard] |
| Jsoup | (đã có từ Phase 13) | `Jsoup.clean(content, Safelist.none())` cho edit content | [VERIFIED: ReviewService import] |
| Flyway | (BOM) | V7 migration | [VERIFIED: V1-V6 đã ship] |
| react-hook-form | 7.55.0 | Edit form (rating + content) — reuse Phase 13 pattern | [VERIFIED: ReviewForm.tsx hiện tại] |
| zod | 3.24.1 | Validation schema | [VERIFIED: ReviewForm.tsx hiện tại] |
| Next.js App Router | 15.x | `router.replace()` + `useSearchParams` cho URL persistence sort | [VERIFIED: existing PDP page.tsx dùng App Router] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Validation | (BOM) | `@Min(1) @Max(5)` cho edit rating, `@Size(max=500)` cho content | Endpoint PATCH validation |
| JUnit 5 + Spring Boot Test | (BOM) | Service unit + Controller MockMvc integration | [VERIFIED: ReviewServiceTest + ReviewControllerTest đã tồn tại từ Phase 13] |
| Testcontainers Postgres | (BOM test scope) | Integration test V7 migration + partial UNIQUE | [VERIFIED: Phase 13] |
| Playwright | (đã có) | E2E spec author edit/delete + admin moderation | [VERIFIED: existing E2E suite] |

### Không thêm gì mới
**Phase 21 không cần install package mới.** Mọi capability đều phủ bởi stack v1.0+v1.1+v1.2 hiện tại.

---

## Approach Summary — Dataflow cho 5 endpoint mới

```
[1] PATCH /api/products/{productId}/reviews/{reviewId}     (author edit)
    Browser → Gateway product-service → ReviewController.editReview
      → parseToken → userId
      → ReviewService.editReview(reviewId, userId, body)
          → findById → exists & deletedAt IS NULL? (else 422 REVIEW_NOT_FOUND)
          → review.userId == userId? (else 403 REVIEW_NOT_OWNER)
          → now - review.createdAt <= editWindowHours? (else 422 REVIEW_EDIT_WINDOW_EXPIRED)
          → Jsoup.clean(content) nếu content thay đổi
          → review.applyEdit(newRating, sanitizedContent)
          → save
          → IF rating changed → recomputeProductRating(productId)
      → ApiResponse 200 toResponse(review)

[2] DELETE /api/products/{productId}/reviews/{reviewId}    (author soft-delete)
    Browser → Gateway → ReviewController.softDeleteReview
      → parseToken → userId
      → ReviewService.softDeleteReview(reviewId, userId)
          → findById → exists & deletedAt IS NULL? (else 404)
          → review.userId == userId? (else 403)
          → review.markDeleted()
          → save
          → recomputeProductRating(productId)   ← LUÔN recompute (review bị remove khỏi avg)
      → 204 No Content

[3] GET /api/products/admin/reviews?page=&size=&filter=all|visible|hidden|deleted
    (rewrite từ gateway product-service-admin → /admin/products/reviews)
    Browser → Gateway → AdminReviewController.list
      → JwtRoleGuard.requireAdmin(authHeader)
      → ReviewService.listAdminReviews(page, size, filter)
          → JPA Specification build:
              all      → no extra predicate
              visible  → deletedAt IS NULL AND hidden = false
              hidden   → hidden = true
              deleted  → deletedAt IS NOT NULL
          → reviewRepo.findAll(spec, PageRequest.of(page, size, Sort.by(DESC, "createdAt")))
          → resolve productSlug per row (Finding 9 — BE JOIN recommended)
      → ApiResponse Page<AdminReviewDTO>

[4] PATCH /api/products/admin/reviews/{reviewId}/visibility    body { hidden: boolean }
    Browser → Gateway → AdminReviewController.setVisibility
      → JwtRoleGuard.requireAdmin
      → ReviewService.setVisibility(reviewId, hidden)
          → findById → review.setHidden(hidden) → save
          → recomputeProductRating(productId)    ← hide loại khỏi avg, unhide đưa lại vào
      → 200

[5] DELETE /api/products/admin/reviews/{reviewId}    (hard-delete)
    Browser → Gateway → AdminReviewController.hardDelete
      → JwtRoleGuard.requireAdmin
      → ReviewService.hardDelete(reviewId)
          → findById → productId = review.productId
          → reviewRepo.delete(review)
          → recomputeProductRating(productId)    ← review biến mất hoàn toàn khỏi avg
      → 204
```

---

## Library / API Patterns

### Finding 1: Gateway routing — KHÔNG có `/api/admin/**`, dùng `/api/products/admin/reviews/**`

[VERIFIED: `sources/backend/api-gateway/src/main/resources/application.yml` lines 84-96]

Gateway pattern thực tế: `/api/{service}/admin/**` → `/admin/{service}/**`. Route `product-service-admin` (`/api/products/admin/**`) đã có và **tự động cover** `/api/products/admin/reviews/**`. Không cần sửa gateway config.

**FE gọi:**
- `GET  /api/products/admin/reviews?page=0&size=20&filter=all`
- `PATCH /api/products/admin/reviews/{id}/visibility`
- `DELETE /api/products/admin/reviews/{id}`

**BE Controller mapping:**
```java
@RestController
@RequestMapping("/admin/products/reviews")  // gateway rewrite: /api/products/admin/reviews → /admin/products/reviews
public class AdminReviewController { ... }
```

**Path conflict caution:** `AdminProductController @RequestMapping("/admin/products")` có `@DeleteMapping("/{id}")`. Spring Web prioritizes literal segments over path variables, nên `DELETE /admin/products/reviews/{id}` (handled bởi `AdminReviewController`) sẽ KHÔNG match `AdminProductController.deleteProduct({id="reviews"})`. [CITED: Spring MVC HandlerMapping path matching — literal beats path-variable]. Vẫn nên thêm test integration verify cả 2 controller cùng tồn tại không xung đột.

> **Đính chính CONTEXT.md D-18:** Câu "route qua gateway `/api/admin/**` đã có (admin products/orders/users)" là **sai sự thật**. Đúng phải là: route `/api/products/admin/**` đã có, AdminReviewController phải nằm dưới `/admin/products/reviews`.

### Finding 2: Spring Data JPA `Sort` — sort-aware finder dùng Pageable

**Pattern recommended (D-27, single method):**

```java
// ReviewRepository.java
public interface ReviewRepository extends JpaRepository<ReviewEntity, String>,
                                          JpaSpecificationExecutor<ReviewEntity> {

  // Public list — visibility filter built-in qua method name
  Page<ReviewEntity> findByProductIdAndDeletedAtIsNullAndHiddenFalse(
      String productId, Pageable pageable);  // Pageable carries Sort

  boolean existsByProductIdAndUserIdAndDeletedAtIsNull(String productId, String userId);

  // Owner check fast-path
  Optional<ReviewEntity> findByIdAndDeletedAtIsNull(String reviewId);

  // Recompute scope (D-08): exclude deleted + hidden
  @Query("SELECT COALESCE(AVG(r.rating), 0), COUNT(r) FROM ReviewEntity r " +
         "WHERE r.productId = :productId AND r.deletedAt IS NULL AND r.hidden = false")
  Object[] computeStats(@Param("productId") String productId);
}
```

```java
// ReviewController.java — sort param mapping (D-12)
@GetMapping
public ApiResponse<Map<String, Object>> listReviews(
    @PathVariable String productId,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size,
    @RequestParam(defaultValue = "newest") String sort) {
  return ApiResponse.of(200, "Reviews listed",
      reviewService.listReviews(productId, page, size, sort));
}
```

```java
// ReviewService.java — switch case → Sort object, fallback newest (graceful, no 400)
private static Sort resolveSort(String sortKey) {
  return switch (sortKey == null ? "newest" : sortKey) {
    case "rating_desc" -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"));
    case "rating_asc"  -> Sort.by(Sort.Order.asc("rating"),  Sort.Order.desc("createdAt"));
    default            -> Sort.by(Sort.Order.desc("createdAt")); // newest + invalid
  };
}

public Map<String, Object> listReviews(String productId, int page, int size, String sortKey) {
  int safePage = Math.max(0, page);
  int safeSize = Math.min(Math.max(1, size), 50);
  Pageable pageable = PageRequest.of(safePage, safeSize, resolveSort(sortKey));
  Page<ReviewEntity> result =
      reviewRepo.findByProductIdAndDeletedAtIsNullAndHiddenFalse(productId, pageable);
  // ... build response map identical to existing pattern
}
```

[VERIFIED: pattern này hợp lệ với Spring Data JPA 3.x — `PageRequest.of(page, size, Sort)` thread-safe vì `Sort` immutable] [CITED: spring.io/projects/spring-data-jpa#sort]

### Finding 3: Soft-delete + admin filter — JPA Specifications API

[ASSUMED — codebase hiện chưa dùng Specifications, nhưng pattern chuẩn cho dynamic filter]

```java
// AdminReviewSpecifications.java (NEW)
public final class AdminReviewSpecifications {
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

```java
// ReviewService.java — admin list
public Map<String, Object> listAdminReviews(int page, int size, String filter) {
  int safePage = Math.max(0, page);
  int safeSize = Math.min(Math.max(1, size), 100);
  Pageable pageable = PageRequest.of(safePage, safeSize,
      Sort.by(Sort.Order.desc("createdAt")));
  Page<ReviewEntity> result = reviewRepo.findAll(
      AdminReviewSpecifications.withFilter(filter), pageable);
  // ... map sang AdminReviewDTO (xem Finding 9 cho slug resolution)
}
```

**Repository phải implement `JpaSpecificationExecutor<ReviewEntity>`** — bổ sung `extends`. Pattern này không can thiệp vào `findByProductIdAnd...` finders cho public list.

[CITED: docs.spring.io/spring-data-jpa/reference/jpa/specifications.html — pattern này stable từ Spring Data JPA 2.x]

### Finding 4: Partial UNIQUE index migration — Postgres + Flyway V7

**V7 migration script (D-20):**

```sql
-- V7__add_review_moderation_columns.sql
-- REV-04 + REV-06: thêm columns moderation, đổi UNIQUE thành partial index để cho phép re-review sau soft-delete.

ALTER TABLE product_svc.reviews
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE NULL,
  ADD COLUMN IF NOT EXISTS hidden     BOOLEAN NOT NULL DEFAULT FALSE;

-- Drop old UNIQUE constraint (V4: uq_review_product_user)
ALTER TABLE product_svc.reviews
  DROP CONSTRAINT IF EXISTS uq_review_product_user;

-- Re-create as PARTIAL unique index — chỉ enforce khi review chưa soft-delete
CREATE UNIQUE INDEX IF NOT EXISTS uq_review_product_user_active
  ON product_svc.reviews (product_id, user_id)
  WHERE deleted_at IS NULL;

-- Index hỗ trợ admin filter + public list visibility WHERE
CREATE INDEX IF NOT EXISTS idx_reviews_visibility
  ON product_svc.reviews (product_id, hidden, deleted_at);
```

**Edge case kiểm tra:** Trên DB hiện trạng (Phase 13 đã ship), UNIQUE `uq_review_product_user` cũ ngăn 2 row cùng `(product_id, user_id)` xuất hiện. Sau migration:
- Tất cả row hiện tại có `deleted_at = NULL` → partial index ngay lập tức enforce uniqueness cho dữ liệu hiện tại.
- KHÔNG có duplicate trước đó (nhờ constraint cũ) → migration chạy thành công không cần dedupe.
- Sau soft-delete: row cũ `deleted_at IS NOT NULL` → loại khỏi partial index → user CAN INSERT row mới với cùng `(product_id, user_id)`.

**Idempotency:** `IF NOT EXISTS` + `IF EXISTS` cho phép migration re-run trên DB đã apply (qua Flyway repair scenarios).

[CITED: postgresql.org/docs/16/indexes-partial.html — partial unique index pattern chuẩn từ Postgres 9.0+]

### Finding 5: ReviewEntity mutators (D-20)

```java
// ReviewEntity.java — bổ sung sau Phase 13 baseline
@Column(name = "deleted_at")
private Instant deletedAt;        // null = active

@Column(nullable = false)
private boolean hidden = false;

// Mutators
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

// Accessors
public Instant deletedAt() { return deletedAt; }
public boolean hidden() { return hidden; }
public boolean isActive() { return deletedAt == null && !hidden; }  // helper cho service
```

> **Existing accessor convention:** ReviewEntity hiện dùng record-style accessors `id()`, `productId()`, etc. Giữ nguyên convention — KHÔNG dùng JavaBean `getXxx()`.

### Finding 6: Spring `@Value` config injection — `app.reviews.edit-window-hours`

[CITED: docs.spring.io/spring-boot/reference/features/external-config.html]

**Pattern best practice 2026:**

```java
// ReviewService.java
@Service
public class ReviewService {
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
  // ...
}
```

**`application.yml`:**
```yaml
app:
  reviews:
    edit-window-hours: 24
  jwt:
    secret: ...   # đã có
```

**Test override (`application-test.yml` hoặc `@TestPropertySource`):**
```java
@SpringBootTest
@TestPropertySource(properties = "app.reviews.edit-window-hours=0")
class ReviewServiceEditExpiredTest { /* test 422 ngay lập tức */ }
```

**Edit window check trong service:**
```java
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
  // ... apply edit
}
```

**Exposure cho FE (D-21):** trả `editWindowHours` trong list response meta hoặc thông qua endpoint riêng `GET /api/products/{id}/reviews/config`. Đơn giản nhất: nhúng vào meta của list response:
```json
{ "content": [...], "totalElements": ..., "config": { "editWindowHours": 24 } }
```

### Finding 7: Next.js 15 App Router — `router.replace()` cập nhật `?sort=` không scroll

[CITED: nextjs.org/docs/app/api-reference/functions/use-router#routerreplace] [CITED: nextjs.org/docs/app/api-reference/functions/use-search-params]

```tsx
// ReviewList.tsx (or ReviewSection.tsx — tuỳ planner)
'use client';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';

const pathname = usePathname();
const router = useRouter();
const searchParams = useSearchParams();

const onSortChange = (newSort: 'newest' | 'rating_desc' | 'rating_asc') => {
  const params = new URLSearchParams(searchParams.toString());
  if (newSort === 'newest') {
    params.delete('sort');     // default — không ghi vào URL (D-13)
  } else {
    params.set('sort', newSort);
  }
  const qs = params.toString();
  router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  // Trigger refetch — pass newSort xuống fetch (controlled state)
};
```

**Initial state read:**
```tsx
const initialSort = (searchParams.get('sort') as SortKey) ?? 'newest';
const [sort, setSort] = useState<SortKey>(initialSort);
```

**Notes:**
- `{ scroll: false }` ngăn Next.js auto-scroll lên top khi URL đổi.
- `router.replace()` (không `push`) — không add history entry → back button không quay về sort cũ.
- PDP slug page hiện chưa dùng query string → sort param không collide. Verify trong Wave 2: grep `useSearchParams` trên `app/products/[slug]/`.

### Finding 8: Admin authorization — reuse `JwtRoleGuard.requireAdmin()`

[VERIFIED: `JwtRoleGuard.java` + `AdminStatsController.java`]

```java
// AdminReviewController.java (NEW)
@RestController
@RequestMapping("/admin/products/reviews")
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
    return ApiResponse.of(200, "Visibility updated",
        reviewService.setVisibility(reviewId, body.hidden()));
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

**Roles claim format** (verified `JwtRoleGuard` lines 50-58): `roles` claim là string CSV (e.g. `"USER"`, `"ADMIN"`, `"ADMIN,USER"`). Split bằng comma. Cần ADMIN trong list. KHÔNG cần thay đổi format.

### Finding 9: `productSlug` resolution cho admin row — BE JOIN recommended

**Tradeoff:**

| Approach | Pros | Cons |
|----------|------|------|
| **BE JPQL JOIN** (recommended) | 1 query, FE simpler, atomic | DTO mapping phức tạp hơn 1 chút |
| FE batch fetch slug | BE đơn giản | N+1 nhìn từ FE — 20 row × 1 fetch hoặc cần `GET /products?ids=...` endpoint chưa có |

**JPQL pattern:**
```java
@Query("SELECT new com.ptit.htpt.productservice.service.AdminReviewProjection(" +
       "  r.id, r.productId, p.slug, p.name, r.userId, r.reviewerName, r.rating, " +
       "  r.content, r.hidden, r.deletedAt, r.createdAt) " +
       "FROM ReviewEntity r JOIN ProductEntity p ON r.productId = p.id " +
       "WHERE (:filter = 'all') OR " +
       "  (:filter = 'visible' AND r.deletedAt IS NULL AND r.hidden = false) OR " +
       "  (:filter = 'hidden' AND r.hidden = true) OR " +
       "  (:filter = 'deleted' AND r.deletedAt IS NOT NULL) " +
       "ORDER BY r.createdAt DESC")
Page<AdminReviewProjection> findAdminReviews(@Param("filter") String filter, Pageable pageable);
```

⚠️ **Hibernate gotcha:** ProductEntity có `@SQLRestriction("deleted_at IS NULL")` (verified Phase 13 patterns) — nên products đã soft-delete sẽ NOT match JOIN, làm review (gắn product đó) biến mất khỏi admin list. **Mitigation:** dùng native query hoặc `@FilterDef` disable trick. Đơn giản hơn: nếu admin chỉ moderate review của products còn active, chấp nhận behavior này. Nếu không, dùng Specification + manual slug fetch (giải pháp dự phòng).

**Recommendation:** Dùng Specification (Finding 3) + tách slug fetch thành Map<productId, slug> qua `productRepo.findAllById(ids)` mỗi page (max 20 ids, 1 query duy nhất). Tránh `@SQLRestriction` interference, đơn giản hơn JPQL JOIN có @SQLRestriction.

```java
// ReviewService.listAdminReviews
Page<ReviewEntity> page = reviewRepo.findAll(spec, pageable);
List<String> productIds = page.getContent().stream().map(ReviewEntity::productId).distinct().toList();
Map<String, String> idToSlug = productRepo.findAllById(productIds).stream()
    .collect(Collectors.toMap(ProductEntity::id, ProductEntity::slug));
List<AdminReviewDTO> dtos = page.getContent().stream()
    .map(r -> AdminReviewDTO.from(r, idToSlug.getOrDefault(r.productId(), null)))
    .toList();
```

### Finding 10: Recompute helper cô lập — gọi từ 5 path mới + 1 path cũ

```java
// ReviewService.java
private void recomputeProductRating(String productId) {
  ProductEntity product = productRepo.findById(productId).orElseThrow();
  Object[] row = reviewRepo.computeStats(productId);  // đã filter deletedAt IS NULL AND hidden = false
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

**Existing path (Phase 13):** `createReview` đã gọi (giữ nguyên).
**New paths (Phase 21):**
1. `editReview` — chỉ gọi nếu `newRating != review.rating()` (optimization).
2. `softDeleteReview` — luôn gọi.
3. `setVisibility(id, true)` — luôn gọi (review biến mất khỏi avg).
4. `setVisibility(id, false)` — luôn gọi (review quay lại avg).
5. `hardDelete(id)` — luôn gọi (sau khi `delete()` trong cùng transaction).

**Verify `ProductEntity.updateRatingStats(BigDecimal.ZERO, 0)` reset đúng** (Discretion point từ CONTEXT.md). Phase 13 ReviewService line 109-125 hiện đã set `avg = ZERO` khi `row` empty/null — nhưng cần test khi count = 0 sau khi delete review cuối cùng:
- Test name: `recompute_resetsToZero_whenAllReviewsDeleted`.

---

## Pitfalls (specific to phase 21)

### Pitfall 1: Quên recompute ở 1 trong 5 path mutation mới → drift avg_rating
**What goes wrong:** Edit review (rating change) hoặc admin hide/unhide không gọi recompute → `avg_rating` cached lệch khỏi thực tế.
**Why it happens:** Recompute logic scattered ở 5 method khác nhau; dev quên 1.
**How to avoid:** Tách `recomputeProductRating(productId)` thành helper method, mỗi mutation method end với 1 dòng `this.recomputeProductRating(review.productId())`. Integration test sau mỗi mutation: query `products.avg_rating` và compare với `SELECT AVG(rating) FROM reviews WHERE product_id=? AND deleted_at IS NULL AND hidden = false`.
**Warning signs:** Manual UAT thấy avg_rating không đổi sau hide review; integration test recompute drift.

### Pitfall 2: Edit endpoint recompute không cần thiết khi chỉ đổi content
**What goes wrong:** User chỉ sửa content (typo fix), không đổi rating → recompute kích hoạt vô nghĩa, lãng phí 1 SELECT + 1 UPDATE per edit.
**Why it happens:** Naive implementation — luôn recompute sau save.
**How to avoid:** Edit method snapshot `oldRating` trước khi `applyEdit`, chỉ recompute nếu `newRating != oldRating`. Test: edit content-only → product.updatedAt không tăng (verify recompute skipped).
**Warning signs:** Performance trace thấy edit content-only trigger 2 query thay vì 1.

### Pitfall 3: Soft-deleted review vẫn giữ FK product_id → admin hard-delete OK, không vi phạm FK
**What goes wrong:** [Hypothesis] Hard-delete review có thể vi phạm FK nếu có table khác reference reviews.id.
**Why it happens:** Schema mở rộng tương lai (helpful_votes, audit log) có thể FK lên reviews.
**How to avoid:** Hiện tại schema reviews chỉ có FK ra ngoài (review.product_id → products.id), KHÔNG có FK reverse. Hard-delete review an toàn. Document: nếu Phase REV-07 thêm `helpful_votes(review_id FK)` → cần ON DELETE CASCADE hoặc soft-delete-only.
**Warning signs:** `org.postgresql.util.PSQLException: violates foreign key constraint`.

### Pitfall 4: JWT claim `roles` thiếu hoặc format sai → admin endpoint trả 403 nhầm
**What goes wrong:** Token cũ chưa có roles=ADMIN, hoặc format ` ADMIN ` (whitespace) khiến `JwtRoleGuard` reject.
**Why it happens:** Tokens cấp trước Phase 9 có thể missing claim.
**How to avoid:** `JwtRoleGuard.requireAdmin` đã `r.trim()` (verified line 55) — handle whitespace. Token cũ hết hạn sau 24h. Plan-checker verify: existing AdminStatsController đã chạy ổn định → pattern proven.
**Warning signs:** 403 trên admin reviews trong khi /admin/products vẫn OK → debug roles claim raw value.

### Pitfall 5: FE `currentUserId` lấy từ `useAuth().user?.id` — verify field tên đúng
**What goes wrong:** [VERIFIED: `AuthProvider.tsx` line 18] `user: { id: string; email: string; name: string } | null`. Truy cập `user?.id` đúng. Sai field name → owner check render sai.
**How to avoid:** `ReviewSection` đã import `useAuth()`. Pass `user?.id` xuống `ReviewList` và `ReviewForm` qua prop.
**Warning signs:** Không thấy nút Sửa/Xoá trên review của chính mình.

### Pitfall 6: `?sort=` URL param collide với existing query params trên PDP
**What goes wrong:** Slug page có sẵn query params (e.g. `?tab=reviews`) → set sort overwrite tab.
**Why it happens:** Naive `router.replace('?sort=newest')` thay full query string.
**How to avoid:** Pattern Finding 7 dùng `new URLSearchParams(searchParams)` clone existing → set/delete `sort` only → preserve các param khác.
**Warning signs:** Đổi sort → tab quay về default.

### Pitfall 7: `@SQLRestriction` trên ProductEntity gây JOIN miss soft-deleted products
**What goes wrong:** Admin list reviews JOIN ProductEntity để lấy slug; nếu product đã `@SQLRestriction("deleted_at IS NULL")` → review của product đó không xuất hiện trong admin list.
**Why it happens:** ProductEntity dùng `@SQLRestriction` (Phase 9+).
**How to avoid:** KHÔNG dùng JPQL JOIN. Dùng pattern Finding 9 — Specification + batch fetch slug qua `productRepo.findAllById()`. Map missing → null slug → FE render "—" placeholder.
**Warning signs:** Admin list trống mặc dù DB có review; reviews của products đã admin-delete biến mất.

### Pitfall 8: Partial UNIQUE index migration khi đã có data inconsistent
**What goes wrong:** [LOW RISK] Nếu vì lý do nào đó DB đã có 2 rows cùng `(product_id, user_id)` với `deleted_at IS NULL`, migration `CREATE UNIQUE INDEX` fail.
**Why it happens:** Constraint cũ ngăn → khả năng cực thấp. Manual SQL injection hoặc disable constraint trong dev.
**How to avoid:** Pre-check query `SELECT product_id, user_id, COUNT(*) FROM reviews WHERE deleted_at IS NULL GROUP BY product_id, user_id HAVING COUNT(*) > 1` trước migration. Ship migration với expectation "0 row" — nếu fail, manual cleanup rồi re-run.
**Warning signs:** Flyway log `ERROR: could not create unique index "uq_review_product_user_active"`.

### Pitfall 9: ReviewForm `mode="edit"` reset state khi cancel
**What goes wrong:** User click Sửa → form hiện initialValues; click Cancel → state nội bộ form dirty không clear → lần sau mở lại thấy giá trị cũ.
**How to avoid:** ReviewForm bind `key={review.id + '-' + editing}` để remount form khi mode đổi. Hoặc explicit `reset(initialValues)` trong useEffect khi prop `mode` đổi.
**Warning signs:** Cancel rồi Edit lại thấy text rác từ session trước.

### Pitfall 10: Admin hard-delete trong khi 2 admin online cùng moderate
**What goes wrong:** Admin A click Hide, Admin B click Hard-delete cùng review trong vài giây → race condition: visibility update sau khi row đã bị delete.
**Why it happens:** Không có optimistic lock (`@Version`).
**How to avoid:** Acceptable cho demo (note rõ trong PITFALLS). Service catch `EmptyResultDataAccessException` từ `setVisibility` hoặc check existence trong `findById` rồi throw 404 thân thiện. Không add `@Version` (defer như PITFALLS.md đã ghi).
**Warning signs:** 500 error trên PATCH visibility.

---

## File-Level Change Inventory

> Đây là input trực tiếp cho `gsd-pattern-mapper` / `gsd-planner`. Phân loại: **CREATE** = file mới, **MODIFY** = sửa file hiện có, **TOUCH** = thêm vài dòng nhỏ.

### Backend — `sources/backend/product-service/`

| File | Action | Notes |
|------|--------|-------|
| `src/main/resources/db/migration/V7__add_review_moderation_columns.sql` | CREATE | Migration script (Finding 4) |
| `src/main/java/com/ptit/htpt/productservice/domain/ReviewEntity.java` | MODIFY | Thêm `deletedAt`, `hidden` fields + mutators `markDeleted/setHidden/applyEdit/isActive` (Finding 5) |
| `src/main/java/com/ptit/htpt/productservice/repository/ReviewRepository.java` | MODIFY | (a) `extends JpaSpecificationExecutor`, (b) thêm `findByProductIdAndDeletedAtIsNullAndHiddenFalse`, (c) `findByIdAndDeletedAtIsNull`, (d) `existsByProductIdAndUserIdAndDeletedAtIsNull`, (e) update `computeStats` JPQL WHERE (Finding 2) |
| `src/main/java/com/ptit/htpt/productservice/repository/AdminReviewSpecifications.java` | CREATE | JPA Specification factory (Finding 3) |
| `src/main/java/com/ptit/htpt/productservice/service/ReviewService.java` | MODIFY | Thêm `editReview`, `softDeleteReview`, `listAdminReviews`, `setVisibility`, `hardDelete`; cô lập `recomputeProductRating(productId)`; sửa `listReviews` để nhận `sort` param (Finding 2, 6, 9, 10) |
| `src/main/java/com/ptit/htpt/productservice/service/AdminReviewDTO.java` | CREATE | Record/DTO chứa productSlug + hidden + deletedAt (Finding 9) |
| `src/main/java/com/ptit/htpt/productservice/web/ReviewController.java` | MODIFY | Thêm `@PatchMapping("/{reviewId}")` + `@DeleteMapping("/{reviewId}")` author endpoints; thêm `?sort=` param vào `listReviews`; embed `editWindowHours` vào response config (Finding 6) |
| `src/main/java/com/ptit/htpt/productservice/web/AdminReviewController.java` | CREATE | 3 endpoints admin (Finding 8) |
| `src/main/resources/application.yml` | TOUCH | Thêm key `app.reviews.edit-window-hours: 24` (Finding 6) |
| `src/test/java/.../service/ReviewServiceTest.java` | MODIFY | Thêm test cases edit/delete/visibility/hardDelete + recompute correctness |
| `src/test/java/.../web/ReviewControllerTest.java` | MODIFY | MockMvc PATCH/DELETE author + 403/422 paths |
| `src/test/java/.../web/AdminReviewControllerTest.java` | CREATE | MockMvc admin role guard + 3 endpoints |
| `src/test/resources/application-test.yml` (nếu chưa có) | TOUCH/CREATE | Override `app.reviews.edit-window-hours` cho test edit-expired path |

### Frontend — `sources/frontend/src/`

| File | Action | Notes |
|------|--------|-------|
| `app/products/[slug]/ReviewSection/ReviewSection.tsx` | MODIFY | (a) state `sort`, (b) read initial từ `useSearchParams`, (c) `router.replace` khi đổi sort (Finding 7), (d) pass `currentUserId={user?.id}` + `editWindowHours` xuống ReviewList, (e) `onEdit/onDelete` handlers, (f) refetch product header avg sau mutation |
| `app/products/[slug]/ReviewSection/ReviewList.tsx` | MODIFY | (a) sort dropdown header (D-13), (b) per-item action slot owner-only (D-21), (c) inline edit form swap (D-22), (d) confirm dialog cho delete (D-23) |
| `app/products/[slug]/ReviewSection/ReviewForm.tsx` | MODIFY | Accept prop `mode: 'create' | 'edit'` + `initialValues?: { rating, content }` + `onCancel?: () => void` (D-22) |
| `app/products/[slug]/ReviewSection/ReviewSection.module.css` | MODIFY | Thêm styles cho `.sortDropdown`, `.actionsRow`, `.editingItem`, `.confirmDialog` |
| `app/admin/reviews/page.tsx` | CREATE | Admin page — copy pattern từ `app/admin/products/page.tsx` (table + filter + pagination) |
| `app/admin/reviews/page.module.css` | CREATE | Reuse hoặc clone từ `/admin/products/page.module.css` |
| `app/admin/layout.tsx` | TOUCH | Thêm `{ href: '/admin/reviews', label: 'Đánh giá', icon: <svg .../> }` vào `navItems` array giữa "Đơn hàng" và "Tài khoản" |
| `services/reviews.ts` | MODIFY | Thêm `editReview()`, `softDeleteReview()`, `listAdminReviews()`, `setReviewVisibility()`, `hardDeleteReview()`. Thêm `sort?: SortKey` param vào `listReviews()`. |
| `types/index.ts` | MODIFY | Extend `Review` với optional `hidden?: boolean`, `deletedAt?: string \| null`. Thêm type `AdminReview = Review & { productSlug: string; productName: string }`. Thêm `SortKey = 'newest' \| 'rating_desc' \| 'rating_asc'`. |
| `tests/e2e/reviews-author-edit.spec.ts` (Playwright) | CREATE | E2E owner edit/delete flow trên PDP |
| `tests/e2e/admin-reviews-moderation.spec.ts` (Playwright) | CREATE | E2E admin hide/unhide/hard-delete |

### Documentation

| File | Action | Notes |
|------|--------|-------|
| `.planning/phases/21-hoan-thien-reviews/21-PLAN-XX.md` | CREATE | Output của planner (4 plans dự kiến: BE migration+entity, BE service+controller, FE author UX, FE admin page + E2E) |

---

## Validation Architecture

> Phase này có `workflow.nyquist_validation: true` (verified `.planning/config.json`).

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers Postgres (BE); Playwright (E2E) |
| Config file | `pom.xml` (test scope đã có); `playwright.config.ts` (đã có) |
| Quick run command (BE service) | `mvn test -pl sources/backend/product-service -Dtest=ReviewServiceTest -q` |
| Quick run command (BE controller) | `mvn test -pl sources/backend/product-service -Dtest=ReviewControllerTest,AdminReviewControllerTest -q` |
| Full BE suite | `mvn test -pl sources/backend/product-service` |
| Quick run E2E | `npx playwright test reviews-author-edit.spec.ts admin-reviews-moderation.spec.ts` |

### Phase Requirements → Test Map

| Req | Behavior | Test Type | Automated Command | File Status |
|-----|----------|-----------|-------------------|-------------|
| REV-04 | Owner edit trong 24h → 200, content sanitized, rating updated | Integration (MockMvc) | `mvn test -Dtest=ReviewControllerTest#editReview_ownerWithinWindow_returns200` | Wave 0 |
| REV-04 | Non-owner edit → 403 REVIEW_NOT_OWNER | Integration | `...#editReview_nonOwner_returns403` | Wave 0 |
| REV-04 | Edit past 24h → 422 REVIEW_EDIT_WINDOW_EXPIRED | Integration (override `edit-window-hours=0`) | `...#editReview_pastWindow_returns422` | Wave 0 |
| REV-04 | Edit deleted review → 422 REVIEW_NOT_FOUND | Integration | `...#editReview_softDeleted_returns422` | Wave 0 |
| REV-04 | Author DELETE → 204 + deletedAt set + recompute | Integration | `...#softDelete_owner_returns204AndRecomputes` | Wave 0 |
| REV-04 | Author re-review sau soft-delete → 201 success (partial UNIQUE) | Integration | `...#reReview_afterSoftDelete_succeeds` | Wave 0 |
| REV-04 | Edit chỉ content (không đổi rating) → KHÔNG trigger recompute | Unit (ReviewServiceTest) | `...ReviewServiceTest#editReview_contentOnly_skipsRecompute` | Wave 0 |
| REV-05 | `?sort=newest` → ORDER BY createdAt DESC | Integration | `...#listReviews_sortNewest_ordersByCreatedAtDesc` | Wave 0 |
| REV-05 | `?sort=rating_desc` → ORDER BY rating DESC, createdAt DESC | Integration | `...#listReviews_sortRatingDesc_ordersCorrectly` | Wave 0 |
| REV-05 | `?sort=rating_asc` → ORDER BY rating ASC, createdAt DESC | Integration | `...#listReviews_sortRatingAsc_ordersCorrectly` | Wave 0 |
| REV-05 | `?sort=invalid_value` → fallback newest, KHÔNG throw 400 | Integration | `...#listReviews_invalidSort_fallbackNewest` | Wave 0 |
| REV-05 | FE đổi sort → URL update + refetch + scroll preserved | E2E (Playwright) | `npx playwright test reviews-author-edit.spec.ts -g "sort dropdown"` | Wave 0 |
| REV-06 | Admin GET list filter=all → trả cả deleted + hidden | Integration | `AdminReviewControllerTest#listAll_includesDeletedAndHidden` | Wave 0 |
| REV-06 | Admin GET list filter=visible → exclude deleted/hidden | Integration | `...#listVisible_excludesHidden` | Wave 0 |
| REV-06 | Admin GET list filter=hidden → only hidden | Integration | `...#listHidden_onlyHidden` | Wave 0 |
| REV-06 | Admin GET list filter=deleted → only soft-deleted | Integration | `...#listDeleted_onlySoftDeleted` | Wave 0 |
| REV-06 | PATCH visibility hidden=true → 200 + recompute (review loại khỏi avg) | Integration | `...#setVisibility_hide_recomputesAvg` | Wave 0 |
| REV-06 | PATCH visibility hidden=false (unhide) → 200 + recompute | Integration | `...#setVisibility_unhide_recomputesAvg` | Wave 0 |
| REV-06 | DELETE hard-delete → 204 + row gone + recompute | Integration | `...#hardDelete_removesRow_recomputes` | Wave 0 |
| REV-06 | Non-admin gọi admin endpoint → 403 ADMIN role required | Integration | `...#listReviews_nonAdmin_returns403` | Wave 0 |
| REV-06 | Admin hide review → user thường KHÔNG thấy trong public list | E2E | `admin-reviews-moderation.spec.ts -g "hidden invisible to user"` | Wave 0 |
| Migration | V7 chạy idempotent + partial UNIQUE works on existing Phase 13 data | Integration (Testcontainers) | `...#v7Migration_appliesPartialUniqueAndAllowsReReview` | Wave 0 |
| Migration | computeStats sau hide loại review khỏi avg | Unit | `ReviewServiceTest#recompute_excludesHiddenAndDeleted` | Wave 0 |
| Recompute | All-deleted product → avg=0, count=0 (no NPE) | Unit | `ReviewServiceTest#recompute_resetsToZero_whenAllReviewsDeleted` | Wave 0 |

### Sampling Rate

- **Per task commit:** `mvn test -pl sources/backend/product-service -Dtest=*Review*,*AdminReview* -q` (≤ 30s)
- **Per wave merge:** Full product-svc suite (`mvn test -pl sources/backend/product-service`)
- **Phase gate (`/gsd-verify-work`):** Full BE suite + E2E specs reviews-* + manual UAT walkthrough trên browser (author edit, delete, sort, admin hide/unhide/hard-delete)

### Wave 0 Gaps

- [ ] `sources/backend/product-service/src/test/java/.../web/AdminReviewControllerTest.java` — covers REV-06 admin endpoints + role guard (403)
- [ ] Extend `ReviewServiceTest.java` — covers edit/delete/visibility/hardDelete + recompute correctness (8 new tests)
- [ ] Extend `ReviewControllerTest.java` — author PATCH/DELETE 200/403/422 paths (6 new tests)
- [ ] `sources/frontend/tests/e2e/reviews-author-edit.spec.ts` — owner edit/delete + sort dropdown
- [ ] `sources/frontend/tests/e2e/admin-reviews-moderation.spec.ts` — admin hide/unhide/hard-delete + filter dropdown
- [ ] V7 migration test fixture (seed 2 review rows in test profile) — verify partial UNIQUE allows re-review after soft-delete

### Manual UAT Checklist (cho `/gsd-verify-work`)

- [ ] User login → vào PDP có review của mình → thấy nút "Sửa" + "Xoá".
- [ ] Click "Sửa" → form inline hiện initialValues → đổi rating + content → submit → toast "Đã cập nhật đánh giá" → list refresh + avg trên header cập nhật.
- [ ] Edit review > 24h → button "Sửa" disabled + tooltip "Đã quá thời hạn chỉnh sửa (24h)".
- [ ] Click "Xoá" → confirm dialog "Xoá đánh giá này? Hành động không thể hoàn tác." → confirm → toast "Đã xoá đánh giá" → review biến mất + avg cập nhật.
- [ ] Sau xoá, click "Đánh giá sản phẩm" lại → submit review mới thành công.
- [ ] Đổi sort dropdown "Đánh giá cao nhất" → list re-order, URL có `?sort=rating_desc`, no scroll jump.
- [ ] Reload page với `?sort=rating_desc` trong URL → dropdown hiện "Đánh giá cao nhất" + list đã sort.
- [ ] Login admin → sidebar có link "Đánh giá" → click → trang `/admin/reviews` hiện table.
- [ ] Filter "Đã ẩn" → table chỉ show hidden rows.
- [ ] Click "Ẩn" trên 1 row visible → toast → row update badge "Ẩn"; mở PDP product đó → review bị ẩn KHÔNG xuất hiện cho user thường + avg_rating trên product header giảm/tăng.
- [ ] Click "Bỏ ẩn" → review xuất hiện lại + avg recompute.
- [ ] Click "Xoá vĩnh viễn" → confirm → row biến mất khỏi DB; SELECT trong DB confirm 0 row; avg recompute.

---

## Project Constraints (from CLAUDE.md)

> Không có `CLAUDE.md` ở repo root (verified `Glob('CLAUDE.md')` không match). Project conventions chính lấy từ `.planning/PROJECT.md`:
> - **Visible-first priority** — phase này 100% visible (đúng định hướng).
> - **Vietnamese cho docs/commits/UI**.
> - **Defer backend hardening invisible** — KHÔNG add Spring Security, KHÔNG add @Version, KHÔNG add audit log (đã list trong Deferred Ideas).
> - **Phase numbering tiếp tục KHÔNG reset** — V7 đúng thứ tự sau V6.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring MVC HandlerMapping prioritize literal segment over path variable, nên `DELETE /admin/products/reviews/{id}` đi vào `AdminReviewController` chứ không match `AdminProductController.deleteProduct({id="reviews"})` | Finding 1 | LOW — well-documented Spring behavior từ 4.x; vẫn nên có integration test verify |
| A2 | `JpaSpecificationExecutor` dùng được kết hợp với derived query method names cùng repository | Finding 3 | LOW — pattern chuẩn từ Spring Data JPA 2.x; verified docs |
| A3 | Partial UNIQUE index Postgres không cần CONCURRENTLY trong Flyway migration | Finding 4 | LOW — table reviews chưa lớn (demo project, < 1000 rows). Production-grade sẽ cần CONCURRENTLY nhưng phase này không quan tâm |
| A4 | `ProductEntity.@SQLRestriction` interfere với JPQL JOIN nhưng KHÔNG ảnh hưởng `productRepo.findAllById(ids)` (Finding 9 mitigation) | Finding 9 | MEDIUM — nếu wrong, admin list miss reviews của products soft-deleted. Mitigation đã propose alternative (Specification + batch fetch). Cần test integration. |
| A5 | Slug field tồn tại trong ProductEntity (đã thấy `p.slug?` ở admin/products/page.tsx) | Finding 9 | LOW — verified gián tiếp |
| A6 | `Pageable.of(page, size, Sort)` thread-safe vì Sort immutable | Finding 2 | LOW — official Spring docs confirm |
| A7 | Token cũ (cấp trước Phase 9 / Phase 21) sẽ expire trong 24h, không cần backward-compat cho `roles` claim missing | Pitfall 4 | LOW — Phase 13 RESEARCH đã verified expirationMs default 24h |
| A8 | Existing `ProductEntity.updateRatingStats(BigDecimal.ZERO, 0)` reset đúng | Finding 10 / Discretion | MEDIUM — chưa verify trực tiếp file; planner nên đọc `ProductEntity.java` trong Wave 0 và fix nếu chưa handle 0 |
| A9 | Playwright suite hiện có Login/Auth helper reusable cho admin user | Validation Architecture | LOW — Phase 9/15 đã ship admin E2E, helper tồn tại |

---

## Open Questions

**Không có câu hỏi blocker.** CONTEXT.md đã lock đủ các quyết định. Một số ambiguity nhỏ đã được giải qua Findings:

1. **~~Slug resolution: BE join vs FE batch?~~** → Resolved: BE Specification + batch fetch via `productRepo.findAllById()` (Finding 9). Tránh @SQLRestriction interference với JOIN.

2. **~~AdminReviewController URL path?~~** → Resolved: `/admin/products/reviews` (gateway rewrite từ `/api/products/admin/reviews`). KHÔNG cần thay gateway config (Finding 1).

3. **~~Edit window expose qua endpoint riêng hay embed vào list response?~~** → Resolved: embed vào response config field, đơn giản hơn (Finding 6 cuối).

Câu hỏi cho `/gsd-verify-work` (không cản plan):
- ProductEntity.updateRatingStats khi count = 0 có handle đúng? (A8) — verify trong Wave 0 task BE.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL | V7 migration | ✓ (Docker Compose) | 16 | — |
| Flyway baseline V1-V6 | V7 sequence | ✓ | — | — |
| Jsoup 1.17.2 | Edit content sanitize | ✓ (Phase 13 đã add vào pom.xml) | 1.17.2 | — |
| JJWT 0.12.7 | Author owner check + admin role guard | ✓ | 0.12.7 | — |
| RestTemplate @Bean (product-svc) | (không cần cho phase 21 — eligibility chỉ ở POST review hiện hữu) | ✓ | — | — |
| Spring Data JPA `JpaSpecificationExecutor` | Admin filter | ✓ (in BOM) | — | — |
| react-hook-form 7.55.0 + zod 3.24.1 | Edit form reuse | ✓ | — | — |
| Playwright | E2E spec mới | ✓ | — | — |

**Không có dependency thiếu.** Phase 21 thuần code mở rộng.

---

## Security Domain

> `security_enforcement` không khai báo trong `.planning/config.json` — treat as enabled per default.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT Bearer parse (JJWT) — manual guard, KHÔNG Spring Security (codebase-wide convention) |
| V3 Session Management | partial | JWT stateless 24h |
| V4 Access Control | **yes — critical** | Owner check (review.userId == JWT sub) cho PATCH/DELETE author; Admin role check cho admin endpoints |
| V5 Input Validation | yes | `@Min(1) @Max(5)` rating; `@Size(max=500)` content; Jsoup sanitize content |
| V6 Cryptography | no | Reuse JWT verify only |

### Known Threat Patterns for stack hiện tại

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| User A edit review của User B (IDOR) | Elevation of Privilege | Service-side ownership check `review.userId().equals(jwtSub)` (Finding 6) |
| Non-admin gọi admin endpoint | Elevation of Privilege | `JwtRoleGuard.requireAdmin()` ở mọi admin handler (Finding 8) |
| Edit window bypass (FE state lừa) | Spoofing | BE re-check `Instant.now().isAfter(deadline)` — D-04 |
| XSS qua content edit | Tampering | Re-sanitize Jsoup mỗi lần edit (D-04) |
| Stored XSS persist nếu admin xem hidden review (admin hardly affected) | Info Disclosure | FE render `{content}` text node — đã safe từ Phase 13 |
| Re-review duplicate sau soft-delete bypass | Tampering | Partial UNIQUE index `WHERE deleted_at IS NULL` enforce DB-level (Finding 4) |
| Mass hard-delete abuse từ stolen admin token | Tampering | Defer audit log (Deferred). Mitigation duy nhất: token expiry 24h. Acceptable cho demo. |
| Race condition double-mutate (admin A hide + admin B delete) | Tampering | Acceptable trade-off — defer @Version (Pitfall 10) |

---

## Sources

### Primary (HIGH confidence — verified codebase trực tiếp)

- [VERIFIED] `.planning/phases/21-hoan-thien-reviews/21-CONTEXT.md` — D-01..D-27 lock
- [VERIFIED] `.planning/phases/13-reviews-ratings/13-CONTEXT.md` — Phase 13 nền móng
- [VERIFIED] `.planning/phases/13-reviews-ratings/13-RESEARCH.md` — Phase 13 patterns đã ship
- [VERIFIED] `.planning/research/PITFALLS.md` — pitfall #6 review moderation @Version note
- [VERIFIED] `sources/backend/product-service/src/main/java/.../domain/ReviewEntity.java` — record-style accessors convention
- [VERIFIED] `sources/backend/product-service/src/main/java/.../web/ReviewController.java` — parseToken pattern, fallback claim "name"
- [VERIFIED] `sources/backend/product-service/src/main/java/.../service/ReviewService.java` — recompute pattern + Jsoup usage hiện hữu
- [VERIFIED] `sources/backend/product-service/src/main/java/.../repository/ReviewRepository.java` — current finder + computeStats
- [VERIFIED] `sources/backend/product-service/src/main/java/.../web/JwtRoleGuard.java` — requireAdmin signature + roles split
- [VERIFIED] `sources/backend/product-service/src/main/java/.../web/AdminProductController.java` + `AdminStatsController.java` — admin URL convention `/admin/products`
- [VERIFIED] `sources/backend/api-gateway/src/main/resources/application.yml` — gateway routes, **không có `/api/admin/**`**
- [VERIFIED] `sources/backend/product-service/src/main/resources/db/migration/V4-V6` — UNIQUE constraint name + V-number sequence
- [VERIFIED] `sources/frontend/src/app/products/[slug]/ReviewSection/*.tsx` — current ReviewSection orchestrator pattern
- [VERIFIED] `sources/frontend/src/app/admin/products/page.tsx` — table + filter + pagination pattern để clone
- [VERIFIED] `sources/frontend/src/app/admin/layout.tsx` — sidebar navItems array shape
- [VERIFIED] `sources/frontend/src/providers/AuthProvider.tsx` — `useAuth().user.id` field name
- [VERIFIED] `sources/frontend/src/services/http.ts` — `httpPatch`, `httpDelete` đã tồn tại
- [VERIFIED] `sources/frontend/src/services/reviews.ts` — current shape + ReviewListResponse type
- [VERIFIED] `sources/frontend/src/types/index.ts` — Review interface
- [VERIFIED] `.planning/config.json` — `nyquist_validation: true` confirmed

### Secondary (MEDIUM confidence — official docs, well-documented patterns)

- [CITED] [Spring Data JPA Reference §Sorting](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html#repositories.special-parameters) — Pageable + Sort thread-safety
- [CITED] [Spring Data JPA Reference §Specifications](https://docs.spring.io/spring-data-jpa/reference/jpa/specifications.html) — JpaSpecificationExecutor pattern
- [CITED] [Spring Boot Reference §Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html) — `@Value` injection
- [CITED] [PostgreSQL 16 §Partial Indexes](https://www.postgresql.org/docs/16/indexes-partial.html) — partial UNIQUE
- [CITED] [Next.js 15 App Router — `useRouter`](https://nextjs.org/docs/app/api-reference/functions/use-router) — `replace({scroll: false})`
- [CITED] [Next.js 15 App Router — `useSearchParams`](https://nextjs.org/docs/app/api-reference/functions/use-search-params)

### Tertiary (LOW confidence — assumptions logged)

- [ASSUMED] Specification API + derived query coexistence trong cùng repository (A2) — ngầm định stable
- [ASSUMED] @SQLRestriction trên ProductEntity ảnh hưởng JPQL JOIN nhưng không findAllById (A4) — cần test verify
- [ASSUMED] ProductEntity.updateRatingStats(0, 0) reset đúng (A8) — Wave 0 verify

---

## Metadata

**Confidence breakdown:**
- Locked decisions interpretation: HIGH — CONTEXT.md đã rõ ràng, không có ambiguity blocking.
- Existing codebase patterns reuse: HIGH — đã đọc trực tiếp 8 source files, patterns proven trong production.
- Gateway routing finding (Finding 1): HIGH — verified application.yml line by line; correction CONTEXT.md mô tả gateway sai sự thật.
- Spring Data JPA Specification + Sort patterns: HIGH (CITED official docs) — không có rủi ro version drift.
- Partial UNIQUE migration safety: HIGH — Postgres 9.0+ feature stable, table còn nhỏ.
- @SQLRestriction interference (Pitfall 7 / A4): MEDIUM — recommendation tránh JOIN, dùng batch fetch.
- ProductEntity.updateRatingStats edge case (A8): MEDIUM — chưa verify file directly.

**Research date:** 2026-05-02
**Valid until:** 2026-06-01 (stable stack)

---

## RESEARCH COMPLETE

**Phase:** 21 — Hoàn Thiện Reviews
**Confidence:** HIGH

### Key Findings

1. **Gateway routing đính chính** — KHÔNG có `/api/admin/**`, dùng `/api/products/admin/reviews/**` → `/admin/products/reviews/**`. CONTEXT.md D-18 mô tả sai gateway.
2. **Recompute helper cô lập** + 5 path mutation mới — risk hotspot cao nhất, mitigation tách function + integration test mỗi path.
3. **Partial UNIQUE index** `WHERE deleted_at IS NULL` cho re-review sau soft-delete — pattern Postgres chuẩn, idempotent.
4. **JPA Specifications + derived queries** kết hợp ở cùng repository — không cần fork repository, không cần native SQL.
5. **ProductEntity @SQLRestriction interferes với JPQL JOIN** — admin list slug resolution dùng `findAllById` batch fetch để tránh.

### File Created
`.planning/phases/21-hoan-thien-reviews/21-RESEARCH.md`

### Confidence Assessment
| Area | Level | Reason |
|------|-------|--------|
| Locked decisions consumed | HIGH | 27/27 decisions ánh xạ vào findings |
| Existing patterns reuse | HIGH | 8 codebase files đọc trực tiếp |
| New patterns (Specification, Sort, partial UNIQUE) | HIGH | CITED official docs |
| Gateway correction | HIGH | Application.yml verified line-by-line |
| Edge cases (recompute drift, slug JOIN) | MEDIUM | A4 + A8 cần Wave 0 verify |

### Open Questions
None — all blockers resolved within research. A8 (ProductEntity.updateRatingStats edge case) là verify-only task trong Wave 0, không cản plan.

### Ready for Planning
Research complete. Planner có thể tạo PLAN.md files với 4 plans dự kiến:
- **Plan 21-01:** BE schema + entity (V7 migration, ReviewEntity mutators, repository finders + Specification, application.yml config)
- **Plan 21-02:** BE service + controllers (editReview/softDelete/admin methods, ReviewController PATCH/DELETE, AdminReviewController, recompute helper, BE tests)
- **Plan 21-03:** FE author UX (services/reviews.ts mở rộng, types, ReviewSection sort + currentUserId, ReviewList sort dropdown + actions, ReviewForm edit mode)
- **Plan 21-04:** FE admin moderation page + sidebar nav + E2E specs (Playwright author edit + admin moderation)
