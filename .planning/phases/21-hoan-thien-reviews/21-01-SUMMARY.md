---
phase: 21
plan: 01
subsystem: product-service / reviews
tags: [backend, migration, jpa, schema, moderation, REV-04, REV-06]
requires:
  - Phase 13 reviews baseline (V4 schema, ReviewEntity, ReviewRepository)
provides:
  - V7 migration adding deleted_at + hidden + partial UNIQUE
  - ReviewEntity moderation mutators (markDeleted/setHidden/applyEdit) + visibility accessors
  - Visibility-aware ReviewRepository finders + visibility-filtered computeStats
  - JpaSpecificationExecutor on ReviewRepository
  - AdminReviewSpecifications factory cho 4-state admin filter
  - Spring config app.reviews.edit-window-hours (default 24)
affects:
  - product-service Spring context (DDL apply lúc startup; entity + repo signature)
tech-stack:
  added: []
  patterns:
    - "Postgres partial UNIQUE INDEX (WHERE deleted_at IS NULL)"
    - "Spring Data JpaSpecificationExecutor"
key-files:
  created:
    - sources/backend/product-service/src/main/resources/db/migration/V7__add_review_moderation_columns.sql
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/AdminReviewSpecifications.java
  modified:
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ReviewEntity.java
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ReviewRepository.java
    - sources/backend/product-service/src/main/resources/application.yml
decisions:
  - "D-20: V7 migration thứ tự ADD COLUMN → DROP CONSTRAINT → CREATE PARTIAL UNIQUE → CREATE INDEX (idempotent IF [NOT] EXISTS)"
  - "D-08: computeStats loại cả deleted_at IS NOT NULL lẫn hidden=true khỏi avg + count"
  - "Anti-pattern: KHÔNG @SQLRestriction trên ReviewEntity (admin cần xem deleted)"
metrics:
  duration: "~10 minutes"
  completed: 2026-05-02
  tasks_completed: 3
  files_changed: 5
requirements: [REV-04, REV-06]
---

# Phase 21 Plan 01: BE schema/entity foundation Summary

Đặt nền móng schema V7 + entity mutators + repository visibility-aware finders + admin Specification + config edit-window cho Phase 21 reviews moderation. Tất cả primitives này blocking cho Plan 21-02 (service/controller).

## What Was Built

### V7 Migration (`V7__add_review_moderation_columns.sql`)

Migration idempotent thêm 2 column moderation và đổi UNIQUE constraint sang partial index để cho phép re-review sau soft-delete.

Statements (đúng thứ tự bắt buộc):
1. `ALTER TABLE … ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE NULL, hidden BOOLEAN NOT NULL DEFAULT FALSE`
2. `ALTER TABLE … DROP CONSTRAINT IF EXISTS uq_review_product_user`
3. `CREATE UNIQUE INDEX IF NOT EXISTS uq_review_product_user_active ON …(product_id, user_id) WHERE deleted_at IS NULL`
4. `CREATE INDEX IF NOT EXISTS idx_reviews_visibility ON …(product_id, hidden, deleted_at)`

### ReviewEntity mở rộng

- Fields mới: `private Instant deletedAt;` (nullable; null = active), `private boolean hidden = false;`
- Mutators mới: `markDeleted()`, `setHidden(boolean)`, `applyEdit(int newRating, String sanitizedContent)` — đều cập nhật `updatedAt`
- Accessors mới (record-style): `deletedAt()`, `hidden()`, `isActive()`
- KHÔNG thêm `@SQLRestriction` (anti-pattern documented)

### ReviewRepository mở rộng

- Extends `JpaSpecificationExecutor<ReviewEntity>` (kế thừa `findAll(Specification, Pageable)`)
- 3 finder visibility-aware:
  - `findByProductIdAndDeletedAtIsNullAndHiddenFalse(String, Pageable): Page<ReviewEntity>`
  - `existsByProductIdAndUserIdAndDeletedAtIsNull(String, String): boolean`
  - `findByIdAndDeletedAtIsNull(String): Optional<ReviewEntity>`
- `computeStats` JPQL được update với `WHERE r.productId = :productId AND r.deletedAt IS NULL AND r.hidden = false` (D-08)
- Phase 13 baseline finders giữ tạm (`findByProductIdOrderByCreatedAtDesc`, `existsByProductIdAndUserId`) — Plan 21-02 sẽ replace callers rồi remove

### AdminReviewSpecifications (mới)

- Final class + private constructor (utility pattern)
- `static Specification<ReviewEntity> withFilter(String filter)` switch trên `"all"|"visible"|"hidden"|"deleted"`
- Default fallback (null hoặc unknown filter) → `cb.conjunction()` ≡ "all"

### Config

- `application.yml` thêm `app.reviews.edit-window-hours: 24` ngay trên block `app.jwt` (giữ nguyên `app.jwt.secret`/`expiration-ms`)

## Pre-V7 Verify Outcomes (Task 0)

- **A4 — `@SQLRestriction` trên ReviewEntity?** OK — ReviewEntity KHÔNG có annotation. Visibility lọc ở repo layer (method names + JPQL WHERE). Admin có thể đọc soft-deleted/hidden review qua repo gọi `findById`/`findAll(spec)`.
- **A8 — `updateRatingStats(BigDecimal.ZERO, 0)` reset behavior?** OK — `ProductEntity.updateRatingStats` đã null-safe (`avgRating != null ? avgRating : ZERO`) và `Math.max(0, reviewCount)`. Recompute reset sau khi mọi review bị soft-delete/hard-delete sẽ pass `BigDecimal.ZERO + 0` (hoặc tương đương COALESCE trả 0) — không NPE, không cần ZERO sentinel cho 21-02.

## Interfaces Exposed cho Plan 21-02

```java
// ReviewEntity
public void markDeleted();
public void setHidden(boolean hidden);
public void applyEdit(int newRating, String sanitizedContent);
public Instant deletedAt();
public boolean hidden();
public boolean isActive();

// ReviewRepository
Page<ReviewEntity> findByProductIdAndDeletedAtIsNullAndHiddenFalse(String, Pageable);
boolean existsByProductIdAndUserIdAndDeletedAtIsNull(String, String);
Optional<ReviewEntity> findByIdAndDeletedAtIsNull(String);
// findAll(Specification<ReviewEntity>, Pageable) — inherited
Object[] computeStats(String productId);  // visibility-filtered

// AdminReviewSpecifications
public static Specification<ReviewEntity> withFilter(String filter);

// application.yml
@Value("${app.reviews.edit-window-hours:24}") long editWindowHours;
```

## Build Verification

`mvn -q -DskipTests compile` (apache-maven-3.9.12 + JDK 17 Adoptium) trên `sources/backend/product-service` → exit code 0. Không chạy `mvn test` (deferred to Plan 21-02). Không apply migration thật (Flyway sẽ chạy tự động khi service start ở 21-02).

## Commits

| Task | Commit  | Message |
|------|---------|---------|
| 0    | (no commit) | Verify-only: A4 + A8 outcomes documented in V7 header |
| 1    | b74ba96 | feat(21-01): thêm V7 migration moderation + config edit-window-hours |
| 2    | 1b0a275 | feat(21-01): mở rộng ReviewEntity + ReviewRepository + AdminReviewSpecifications |

## Deviations from Plan

None — plan executed exactly như written. Wave-0 verify A4 + A8 đều "OK" → V7 header ghi 2 dòng OK; không cần tạo issue ZERO-sentinel cho 21-02.

## Deferred / Out of Scope

- ReviewService 5 mutation paths (editReview, softDeleteReview, listAdminReviews, setVisibility, hardDelete) → Plan 21-02
- ReviewController PATCH/DELETE author endpoints → Plan 21-02
- AdminReviewController + AdminReviewDTO → Plan 21-02
- Test files (ReviewServiceTest extension, AdminReviewControllerTest) → Plan 21-02
- FE work (sort dropdown, owner edit/delete, /admin/reviews page) → Plan 21-03/21-04

## Self-Check: PASSED

Files verified to exist:
- FOUND: sources/backend/product-service/src/main/resources/db/migration/V7__add_review_moderation_columns.sql
- FOUND: sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/AdminReviewSpecifications.java
- FOUND: sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ReviewEntity.java (modified)
- FOUND: sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ReviewRepository.java (modified)
- FOUND: sources/backend/product-service/src/main/resources/application.yml (modified)

Commits verified:
- FOUND: b74ba96 feat(21-01): thêm V7 migration moderation + config edit-window-hours
- FOUND: 1b0a275 feat(21-01): mở rộng ReviewEntity + ReviewRepository + AdminReviewSpecifications

Compile: `mvn compile` exit 0.
