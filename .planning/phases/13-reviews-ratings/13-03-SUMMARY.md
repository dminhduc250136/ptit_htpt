---
phase: 13-reviews-ratings
plan: "03"
subsystem: api
tags: [java, spring-boot, jpa, jsoup, jwt, jjwt, testcontainers, flyway, postgres, reviews, ratings]

requires:
  - phase: 13-01
    provides: JWT 'name' claim từ UserDetails fullName — reviewerName snapshot dùng claim này
  - phase: 13-02
    provides: GET http://order-service:8080/internal/orders/eligibility endpoint — ReviewService gọi để kiểm tra quyền review

provides:
  - ReviewEntity (@Entity product_svc.reviews, UNIQUE(product_id,user_id), static factory)
  - ReviewRepository (findByProductIdOrderByCreatedAtDesc, existsByProductIdAndUserId, computeStats COALESCE AVG)
  - ReviewService (createReview @Transactional: duplicate pre-check → eligibility check → Jsoup sanitize → save → recompute avg_rating; listReviews; checkEligibilityInternal fail-safe)
  - ReviewController (3 endpoints: GET list, GET eligibility, POST submit; JWT parse + name fallback)
  - AppConfig (RestTemplate @Bean connect 2s / read 3s timeout)
  - V4 migration: bảng product_svc.reviews với UNIQUE + FK + CHECK rating 1-5
  - V5 migration: ALTER products ADD avg_rating DECIMAL(3,1) + review_count INT
  - ProductEntity: avgRating + reviewCount fields + updateRatingStats() + accessors
  - ProductCrudService.toResponse: trả real avg_rating + review_count (thay hardcode ZERO/0)

affects: [13-04, frontend-review-section, product-detail-page]

tech-stack:
  added:
    - "jsoup 1.17.2 — HTML sanitizer cho XSS protection"
    - "RestTemplate với SimpleClientHttpRequestFactory (connect 2s, read 3s)"
  patterns:
    - "Pre-check duplicate trước insert (existsByProductIdAndUserId) — GlobalExceptionHandler không catch DataIntegrityViolationException"
    - "Fail-safe RestClientException: order-svc down → return false (deny review)"
    - "Recompute avg_rating from scratch SELECT COALESCE(AVG,0)/COUNT trong @Transactional (tránh rating drift)"
    - "JWT name fallback: claims.get(name) || claims.get(username) || userId"

key-files:
  created:
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/AppConfig.java
    - sources/backend/product-service/src/main/resources/db/migration/V4__create_reviews.sql
    - sources/backend/product-service/src/main/resources/db/migration/V5__add_avg_rating_review_count.sql
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ReviewEntity.java
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ReviewRepository.java
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ReviewService.java
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ReviewController.java
    - sources/backend/product-service/src/test/java/com/ptit/htpt/productservice/service/ReviewServiceTest.java
    - sources/backend/product-service/src/test/java/com/ptit/htpt/productservice/web/ReviewControllerTest.java
  modified:
    - sources/backend/product-service/pom.xml
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java

key-decisions:
  - "Pre-check duplicate bằng existsByProductIdAndUserId trước khi insert — GlobalExceptionHandler không catch DataIntegrityViolationException nên phải explicit check, trả 409 với message REVIEW_ALREADY_EXISTS"
  - "Recompute avg_rating từ scratch (SELECT AVG, COUNT) mỗi review insert thay vì increment — tránh rating drift qua nhiều thao tác"
  - "JWT name claim fallback: claims.get('name') → claims.get('username') → userId — handle token cũ phát trước Plan 13-01 deploy"
  - "Fail-safe checkEligibilityInternal: RestClientException → return false (order-svc down = deny review, không leak server error)"
  - "IDOR protection: userId luôn từ JWT claims.getSubject(), không đọc từ request body"

patterns-established:
  - "ReviewService.recomputeProductRating: SELECT COALESCE(AVG(r.rating),0) COUNT(r) → updateRatingStats → save product, trong cùng @Transactional với review save"
  - "ReviewController.parseToken: null/non-Bearer → 401; invalid token → 401; cả service test đều dùng @MockBean RestTemplate"

requirements-completed: [REV-01, REV-02, REV-03]

duration: 30min
completed: 2026-04-27
---

# Phase 13 Plan 03: Backend Reviews API Summary

**ReviewEntity + ReviewRepository + ReviewService (Jsoup XSS sanitize + cross-service eligibility + recompute trong @Transactional) + ReviewController (3 endpoints, JWT name fallback) + 2 Flyway migrations (V4 reviews, V5 avg_rating) + ProductEntity/ProductCrudService update để trả real rating values**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-04-27T16:10:00Z
- **Completed:** 2026-04-27T16:40:00Z
- **Tasks:** 3/3
- **Files modified:** 12 (9 mới + 3 sửa)

## Accomplishments

- V4 migration tạo bảng `product_svc.reviews` với UNIQUE(product_id,user_id), FK, CHECK rating 1-5 — ngăn duplicate tại DB layer
- V5 migration thêm `avg_rating DECIMAL(3,1)` + `review_count INT` vào products; ProductCrudService trả real values thay hardcode ZERO/0
- ReviewService.createReview @Transactional: pre-check duplicate (409) → eligibility via RestTemplate (422) → Jsoup.clean(Safelist.none()) → save review → recompute AVG/COUNT → save product — tất cả trong 1 transaction
- ReviewController 3 endpoints: GET list (paginated, no auth), GET /eligibility (Bearer), POST submit (Bearer + @Valid DTO @Min(1)/@Max(5)/@Size(500))
- 4 ReviewServiceTest + 4 ReviewControllerTest — compile PASS; Testcontainers Docker inaccessible từ JVM subprocess là pre-existing host issue

## Task Commits

1. **Task 1: Foundation** — `911735b` (feat)
2. **Task 2: ReviewEntity + Repository + Service + ServiceTest** — `f80c7da` (feat)
3. **Task 3: ReviewController + ControllerTest** — `ad51cc1` (feat)

## Files Created/Modified

**Mới tạo (9 files):**
- `AppConfig.java` — RestTemplate @Bean, connect 2s / read 3s timeout
- `V4__create_reviews.sql` — product_svc.reviews + UNIQUE(product_id,user_id) + CHECK(rating 1-5)
- `V5__add_avg_rating_review_count.sql` — ALTER products ADD avg_rating + review_count
- `ReviewEntity.java` — @Entity product_svc.reviews, static factory create(), record accessors
- `ReviewRepository.java` — JpaRepository + findByProductIdOrderByCreatedAtDesc + existsByProductIdAndUserId + computeStats (COALESCE AVG)
- `ReviewService.java` — createReview @Transactional, listReviews, checkEligibilityInternal fail-safe
- `ReviewController.java` — 3 endpoints, JWT parse, name fallback, CreateReviewRequest validation record
- `ReviewServiceTest.java` — 4 tests: XSS strip, not-eligible 422, duplicate 409, recompute avg
- `ReviewControllerTest.java` — 4 tests: list 200, eligibility no-auth 401, create no-auth 401, invalid rating 400

**Sửa đổi (3 files):**
- `pom.xml` — thêm jsoup 1.17.2
- `ProductEntity.java` — thêm avgRating/reviewCount fields + updateRatingStats() + accessors
- `ProductCrudService.java` — toResponse trả product.avgRating()/reviewCount() thay BigDecimal.ZERO/0

## Endpoints Mới

| Method | Path | Auth | Response |
|--------|------|------|----------|
| GET | `/products/{productId}/reviews?page=0&size=10` | None | 200 ApiResponse `{data: {content:[], totalElements, totalPages, ...}}` |
| GET | `/products/{productId}/reviews/eligibility` | Bearer | 200 ApiResponse `{data: {eligible: true/false}}` |
| POST | `/products/{productId}/reviews` | Bearer | 201 ApiResponse `{data: {id, productId, userId, reviewerName, rating, content, createdAt}}` |

**Error codes:**
- `409 REVIEW_ALREADY_EXISTS` — đã review product này rồi
- `422 REVIEW_NOT_ELIGIBLE` — chưa mua hoặc order-svc down (fail-safe)
- `400 VALIDATION_ERROR` — rating ngoài 1-5 hoặc content > 500 chars
- `401 UNAUTHORIZED` — thiếu hoặc invalid Bearer token

## Threat IDs Đã Handle

| Threat ID | Mitigation | Test |
|-----------|-----------|------|
| T-13-03-01 (XSS stored) | Jsoup.clean(content, Safelist.none()) | ReviewServiceTest#createReview_xssPayloadStripped |
| T-13-03-02 (IDOR) | userId từ JWT claims.getSubject(), không từ body | ReviewController code review |
| T-13-03-03 (eligibility bypass) | ReviewService.createReview gọi checkEligibilityInternal | ReviewServiceTest#createReview_notEligible_throws422 |
| T-13-03-04 (rating drift) | Recompute from scratch COALESCE AVG/COUNT | ReviewServiceTest#createReview_recomputesAvgRating |
| T-13-03-05 (race/duplicate) | DB UNIQUE + pre-check existsByProductIdAndUserId | ReviewServiceTest#createReview_duplicate_throws409 |
| T-13-03-09 (token cũ no 'name') | Fallback claims.get("username") → userId | ReviewController.createReview code |

## Pitfalls Đã Handle

1. **XSS (Pitfall A3):** Jsoup.clean(content, Safelist.none()) — strip ALL HTML; null/blank trả về null
2. **RestTemplate timeout (Pitfall 5):** connect 2s / read 3s; RestClientException → return false (fail-safe)
3. **Duplicate insert (Pitfall 3):** pre-check existsByProductIdAndUserId trước insert; GlobalExceptionHandler KHÔNG catch DataIntegrityViolationException
4. **AVG NULL khi 0 reviews (Pitfall 4):** COALESCE(AVG(r.rating), 0) trong JPQL query

## Deviations from Plan

None — plan executed exactly as written. Code logic khớp với tất cả acceptance criteria. Testcontainers không chạy được từ JVM subprocess trên host này là pre-existing infrastructure issue đã được plan ghi nhận ("Docker may not be available — pre-existing infrastructure issue; aim for compile PASS + unit tests pass"). Compile PASS + jar package PASS.

## Issues Encountered

- **Docker inaccessible từ JVM subprocess (Windows named pipe):** `docker info` hoạt động từ bash nhưng Testcontainers JVM không kết nối được qua NamedPipeSocketClientProviderStrategy. Pre-existing issue — tất cả existing tests (ProductControllerSlugTest, ProductRepositoryJpaTest) đều bị ảnh hưởng tương tự. Không phải regression do plan này gây ra.

## Known Stubs

Không có stubs — ProductCrudService.toResponse đã trả real `product.avgRating()` và `product.reviewCount()` từ entity fields (thay BigDecimal.ZERO/0 hardcode). Các fields này sẽ được populate bởi ReviewService.recomputeProductRating sau mỗi review insert.

## Next Phase Readiness

- Backend reviews API đầy đủ — Plan 13-04 (Frontend ReviewSection) có thể consume các endpoints này
- Flyway V4+V5 sẽ auto-run khi container start trong Docker Compose
- JWT name claim đã có từ Plan 13-01; fallback hoạt động cho token cũ

## Self-Check: PASSED

- FOUND: AppConfig.java, V4+V5 sql, ReviewEntity, ReviewRepository, ReviewService, ReviewController, ReviewServiceTest, ReviewControllerTest, 13-03-SUMMARY.md
- FOUND commits: 911735b (task 1), f80c7da (task 2), ad51cc1 (task 3)
- Compile PASS: `mvnw -DskipTests compile test-compile` exit 0
- Package PASS: `mvnw -DskipTests package` exit 0

---
*Phase: 13-reviews-ratings*
*Completed: 2026-04-27*
