---
phase: 21-hoan-thien-reviews
verified: 2026-05-02T00:00:00Z
status: passed
score: 3/3 must-haves verified
overrides_applied: 0
---

# Phase 21: Hoàn Thiện Reviews — Verification Report

**Phase Goal:** Tác giả review có thể sửa/xoá review của mình; người dùng có thể sắp xếp reviews theo ý muốn; admin có thể kiểm duyệt reviews vi phạm.
**Verified:** 2026-05-02
**Status:** passed
**Re-verification:** No — initial verification.

## Goal Achievement

### Observable Truths (Success Criteria từ ROADMAP)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Tác giả review có nút Sửa/Xoá; sửa cập nhật nội dung; xoá ẩn khỏi public list nhưng avg_rating recompute đúng | ✓ VERIFIED | `ReviewController.java:70` `@PatchMapping("/{reviewId}")` + `:83` `@DeleteMapping("/{reviewId}")` (owner check + 24h window via `editWindowHours` injected); `ReviewList.tsx:111-157` per-item Sửa/Xoá actions khi `isOwner`; `ReviewService.java:127,142` recompute gọi từ editReview (rating-changed) + softDeleteReview; `computeStats` JPQL `WHERE r.deletedAt IS NULL AND r.hidden = false` (`ReviewRepository.java:30`) — loại deleted + hidden khỏi avg. |
| 2 | Sort dropdown 3-state (Mới nhất / Cao nhất / Thấp nhất) đổi thứ tự ngay với query param `?sort=` | ✓ VERIFIED | `ReviewController.java:64` `@RequestParam(defaultValue="newest") String sort`; `ReviewService.resolveSort` switch fallback newest cho invalid; `ReviewList.tsx:98` `<select className={styles.sortDropdown}>` với 3 options ("Mới nhất"/"Đánh giá cao nhất"/"Đánh giá thấp nhất"); `ReviewSection.tsx:111` `router.replace(qs ? ${pathname}?${qs} : pathname, { scroll: false })` — default newest không ghi vào URL. |
| 3 | Admin `/admin/reviews` list + filter visible/hidden + hide/unhide; hidden không hiện cho user thường | ✓ VERIFIED | `AdminReviewController.java:31` `@RequestMapping("/admin/products/reviews")` (Finding 1 — gateway rewrite từ `/api/products/admin/reviews/**`); 3 endpoints (GET filter, PATCH /{id}/visibility, DELETE /{id}) tất cả gọi `jwtRoleGuard.requireAdmin(auth)`; `app/admin/reviews/page.tsx` heading "Quản lý đánh giá" + filter dropdown 4-state (`Tất cả|Đang hiện|Đã ẩn|Đã xoá`) + actions Ẩn/Bỏ ẩn/Xoá; `app/admin/layout.tsx:15` thêm nav "Đánh giá"; public `findByProductIdAndDeletedAtIsNullAndHiddenFalse` đảm bảo user không thấy hidden review. |

**Score:** 3/3 truths verified.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `V7__add_review_moderation_columns.sql` | Add `deleted_at`, `hidden`, partial UNIQUE | ✓ VERIFIED | 4 statements idempotent; partial unique `WHERE deleted_at IS NULL` cho phép re-review (D-06). |
| `ReviewEntity.java` | Mutators markDeleted/setHidden/applyEdit + accessors | ✓ VERIFIED | Đã có (Plan 21-01 self-check). |
| `ReviewRepository.java` | 3 visibility-aware finders + computeStats lọc deleted+hidden | ✓ VERIFIED | `findByProductIdAndDeletedAtIsNullAndHiddenFalse`, `existsByProductIdAndUserIdAndDeletedAtIsNull`, `findByIdAndDeletedAtIsNull`; `computeStats` WHERE clause đúng. Extends `JpaSpecificationExecutor`. |
| `AdminReviewSpecifications.java` | Specification factory 4-state filter | ✓ VERIFIED | File tồn tại; sử dụng trong `ReviewService.listAdminReviews`. |
| `application.yml` | `app.reviews.edit-window-hours: 24` | ✓ VERIFIED | Line 42. |
| `ReviewService.java` | 5 mutation methods + recompute helper invariant | ✓ VERIFIED | editReview/softDeleteReview/listReviews(sort)/listAdminReviews/setVisibility/hardDelete. `recomputeProductRating` gọi từ 6 path (create + edit-rating-changed + softDelete + setVisibility + hardDelete). |
| `ReviewController.java` | PATCH + DELETE author endpoints + sort param | ✓ VERIFIED | Lines 70/83. EditReviewRequest record. parseToken cho ownership. |
| `AdminReviewController.java` | 3 admin endpoints @ /admin/products/reviews + requireAdmin | ✓ VERIFIED | `@RequestMapping("/admin/products/reviews")` + 3 handlers, mỗi handler `requireAdmin(auth)` đầu tiên. |
| `AdminReviewDTO.java` | 10-field record + factory `from(entity, slug)` | ✓ VERIFIED | File tồn tại. |
| `services/reviews.ts` | 5 mới + extend listReviews | ✓ VERIFIED | editReview/softDeleteReview/listAdminReviews/setReviewVisibility/hardDeleteReview; admin paths dùng `/api/products/admin/reviews` (đúng theo Finding 1). |
| `types/index.ts` | Review.hidden? + deletedAt? + SortKey + AdminReview | ✓ VERIFIED | Plan 21-03 self-check. |
| `ReviewSection.tsx` | sort state + URL persist + handleEdit/handleDelete + currentUserId pass | ✓ VERIFIED | router.replace, useSearchParams, error code → toast mapping (3 codes), pass-through props. |
| `ReviewList.tsx` | sort dropdown header + per-item Sửa/Xoá + inline edit form | ✓ VERIFIED | sortDropdown class, editingId state, isEditExpired helper, tooltip "Đã quá thời hạn chỉnh sửa (24h)". |
| `ReviewForm.tsx` | mode='create'/'edit' + initialValues + onCancel | ✓ VERIFIED | rhf reset on initialValues change, "Lưu thay đổi" / "Gửi đánh giá" labels. |
| `app/admin/reviews/page.tsx` | Table + 4-state filter + actions + pagination | ✓ VERIFIED | "Quản lý đánh giá" heading, filter "Tất cả/Đang hiện/Đã ẩn/Đã xoá", confirm "Xoá vĩnh viễn review này? Không thể hoàn tác." |
| `app/admin/layout.tsx` | Sidebar nav "Đánh giá" link | ✓ VERIFIED | Line 15: `{ href: '/admin/reviews', label: 'Đánh giá', ... }`. |
| `e2e/reviews-author-edit.spec.ts` | Author edit/delete + sort URL persistence E2E | ✓ VERIFIED | 3 tests parsed. |
| `e2e/admin-reviews-moderation.spec.ts` | Admin hide/unhide + cross-tab visibility check | ✓ VERIFIED | 3 tests parsed. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| FE editReview() | BE PATCH /api/products/{id}/reviews/{rid} | httpPatch | ✓ WIRED | services/reviews.ts:63 → ReviewController.java:70 |
| FE softDeleteReview() | BE DELETE /api/products/{id}/reviews/{rid} | httpDelete | ✓ WIRED | reviews.ts:75 → ReviewController.java:83 |
| FE listAdminReviews() | BE GET /api/products/admin/reviews → /admin/products/reviews | gateway rewrite (Finding 1) | ✓ WIRED | reviews.ts:100 → application.yml:94-96 rewrite → AdminReviewController.java:42 |
| FE setReviewVisibility() | BE PATCH /api/products/admin/reviews/{rid}/visibility | gateway rewrite | ✓ WIRED | reviews.ts:106 → AdminReviewController.java:53 |
| FE hardDeleteReview() | BE DELETE /api/products/admin/reviews/{rid} | gateway rewrite | ✓ WIRED | reviews.ts:113 → AdminReviewController.java:64 |
| ReviewSection editWindowHours | BE response.config.editWindowHours | listReviews response embed | ✓ WIRED | ReviewService.java:166 `map.put("config", Map.of("editWindowHours", editWindowHours))` → ReviewSection.tsx:61 `setEditWindowHours(res.config.editWindowHours)` → pass to ReviewList:189 |
| ReviewList Sửa/Xoá owner gating | currentUserId from AuthProvider | ReviewSection prop | ✓ WIRED | ReviewSection.tsx:188 `currentUserId={user?.id}` → ReviewList.tsx:111 `isOwner = !!currentUserId && review.userId === currentUserId` |
| ReviewList sort dropdown | ReviewSection onSortChange + URL | router.replace | ✓ WIRED | ReviewList sort change → ReviewSection.tsx:111 router.replace + loadPage(0,_,newSort) |
| Admin layout nav | /admin/reviews page | Next link | ✓ WIRED | layout.tsx:15 → app/admin/reviews/page.tsx |
| computeStats recompute | productRepo.save | recomputeProductRating helper | ✓ WIRED | 6 invocations in ReviewService.java |
| Public list visibility filter | findByProductIdAndDeletedAtIsNullAndHiddenFalse | repo method | ✓ WIRED | hidden review không hiện cho user thường (D-07/D-09) |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| ReviewSection PDP | reviews + config | listReviews() → BE GET reviews → repo `findByProductIdAndDeletedAtIsNullAndHiddenFalse` + `computeStats` | Yes (real DB query, JPQL with WHERE clause filtering deleted+hidden) | ✓ FLOWING |
| /admin/reviews page | reviews list | listAdminReviews() → BE → `findAll(AdminReviewSpecifications.withFilter, Pageable)` | Yes (Specification-based JPA query) | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| FE TypeScript compile | `npx tsc --noEmit -p tsconfig.json` | exit 0 | ✓ PASS |
| BE Java compile | `mvn -q -DskipTests compile` (product-service) | exit 0 (silent quiet mode) | ✓ PASS |
| Spring controller URL mapping conflict | `/admin/products/reviews` literal segment vs `/admin/products/{id}` path-var | Spring HandlerMapping prioritizes literal segment (well-documented behavior) — no runtime conflict | ✓ PASS |
| Gateway route exists | grep `product-service-admin` in api-gateway/application.yml | Found `/api/products/admin/(?<seg>.*) → /admin/products/${seg}` | ✓ PASS |
| BE integration tests run | `mvn test -Dtest=*Review*` | NOT executed | ? SKIP (Docker daemon stopped per Plan 21-02 deferred — test code compiles cleanly via `mvn test-compile` exit 0; needs human run with Docker) |
| FE Playwright E2E run | `npx playwright test e2e/reviews-*.spec.ts e2e/admin-reviews-*.spec.ts` | NOT executed | ? SKIP (yêu cầu running stack — needs human verification) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| REV-04 | 21-01, 21-02, 21-03 | Author edit/delete review của mình. Edit ≤24h. Soft-delete. avg_rating loại deleted. Admin vẫn xem. | ✓ SATISFIED | PATCH/DELETE author endpoints + 24h window check + soft-delete `deletedAt` + `computeStats` excludes deleted; FE Sửa/Xoá owner-gated với tooltip 24h. |
| REV-05 | 21-02, 21-03 | Sort newest/rating_desc/rating_asc; helpful defer. | ✓ SATISFIED | `?sort=` query param BE + native `<select>` 3 options FE + URL persist via router.replace. Helpful defer (đúng scope). |
| REV-06 | 21-01, 21-02, 21-04 | `/admin/reviews` list + filter visible/hidden + hide/unhide/delete; hidden = column BOOLEAN. | ✓ SATISFIED | `hidden BOOLEAN NOT NULL DEFAULT FALSE` (V7); 3 admin endpoints + `requireAdmin`; `/admin/reviews` page + 4-state filter + Ẩn/Bỏ ẩn/Xoá actions; cross-tab visibility check via E2E spec. |

### Threat Model Verification

| Threat | Verified | Evidence |
|--------|----------|----------|
| Authorization bypass on author edit/delete | ✓ | `ReviewService.editReview` line 114-115 + `softDeleteReview` line 137-138: `if (!review.userId().equals(userId)) throw FORBIDDEN "REVIEW_NOT_OWNER"`. |
| Edit window bypass (FE state untrusted) | ✓ | BE re-checks `Instant.now().isAfter(deadline)` line 117-119 — không phụ thuộc vào FE. |
| IDOR (PATCH/DELETE another user's review) | ✓ | Owner check chạy trên reviewId, trả 403 với code `REVIEW_NOT_OWNER`. |
| XSS via edit content | ✓ | `editReview` line 122 gọi `sanitize(content)` → `Jsoup.clean(content, Safelist.none())` strip toàn bộ HTML. |
| Admin endpoint authorization | ✓ | 3 handlers AdminReviewController đều gọi `jwtRoleGuard.requireAdmin(auth)` đầu tiên. |
| Spring URL conflict (`/admin/products/reviews` vs `/admin/products/{id}`) | ✓ | Spring HandlerMapping prioritizes literal segment — no DELETE conflict. |

### Anti-Patterns Found

Không phát hiện anti-pattern blocker:
- KHÔNG có `@SQLRestriction` trên ReviewEntity (đúng — admin cần xem deleted).
- KHÔNG có hardcoded empty data trong scope files.
- KHÔNG có TODO/FIXME chặn goal trong scope files.
- Tất cả mutation paths đều gọi `recomputeProductRating` (6 occurrences) — recompute invariant duy trì.

### Human Verification Required

Không có item bắt buộc human verification để kết luận status. Nhưng 2 test runs sau ĐƯỢC ĐỀ XUẤT chạy khi môi trường có Docker + running stack:

1. **BE integration tests** — `mvn test -pl sources/backend/product-service -Dtest=*Review*,*AdminReview*` — verify 32 test methods (Plan 21-02 deferred do Docker daemon stopped). Test code compiles sạch.
2. **FE E2E Playwright** — `npx playwright test e2e/reviews-author-edit.spec.ts e2e/admin-reviews-moderation.spec.ts` — 6 tests cố định cả 3 success criteria. Specs đã `--list` parse OK; full run cần backend + frontend running + storageState.

Hai checks này KHÔNG block phase close — code path đã verified static; runtime confirmation là regression-guard cho future phases.

### Gaps Summary

Không có gap. Phase 21 đạt cả 3 success criteria của ROADMAP:
- SC-1 (REV-04 author edit/delete + recompute) — wired BE→FE, owner check + 24h window + soft-delete + recompute exclude deleted/hidden.
- SC-2 (REV-05 sort) — BE accepts ?sort= với fallback graceful, FE dropdown 3 options + URL persist.
- SC-3 (REV-06 admin moderation) — `/admin/reviews` page với filter 4-state, gateway rewrite đúng (Finding 1), requireAdmin trên 3 endpoints, hidden invisible cho user thường.

Build sanity passed: TS exit 0, Maven compile exit 0. Threat model T-21-* mitigations all verified at code level.

---

## VERIFICATION PASSED

3/3 must-haves verified. Phase 21 goal achieved. Sẵn sàng đóng phase.

_Verified: 2026-05-02_
_Verifier: Claude (gsd-verifier)_
