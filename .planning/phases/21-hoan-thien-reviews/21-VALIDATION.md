---
phase: 21
phase_slug: hoan-thien-reviews
date: 2026-05-02
source: 21-RESEARCH.md §"Validation Architecture"
nyquist_enabled: true
---

# Phase 21: Hoàn Thiện Reviews — Validation Strategy

> Phase này có `workflow.nyquist_validation: true` (verified `.planning/config.json`).
> File này trích xuất canonical từ `21-RESEARCH.md` §"Validation Architecture" để thoả Dimension 8e gate.

## Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers Postgres (BE); Playwright (E2E) |
| Config file | `pom.xml` (test scope đã có); `playwright.config.ts` (đã có) |
| Quick run command (BE service) | `mvn test -pl sources/backend/product-service -Dtest=ReviewServiceTest -q` |
| Quick run command (BE controller) | `mvn test -pl sources/backend/product-service -Dtest=ReviewControllerTest,AdminReviewControllerTest -q` |
| Full BE suite | `mvn test -pl sources/backend/product-service` |
| Quick run E2E | `npx playwright test reviews-author-edit.spec.ts admin-reviews-moderation.spec.ts` |

## Phase Requirements → Test Map

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

## Sampling Rate

- **Per task commit:** `mvn test -pl sources/backend/product-service -Dtest=*Review*,*AdminReview* -q` (≤ 30s)
- **Per wave merge:** Full product-svc suite (`mvn test -pl sources/backend/product-service`)
- **Phase gate (`/gsd-verify-work`):** Full BE suite + E2E specs reviews-* + manual UAT walkthrough trên browser (author edit, delete, sort, admin hide/unhide/hard-delete)

## Wave 0 Gaps

- [ ] `sources/backend/product-service/src/test/java/.../web/AdminReviewControllerTest.java` — covers REV-06 admin endpoints + role guard (403)
- [ ] Extend `ReviewServiceTest.java` — covers edit/delete/visibility/hardDelete + recompute correctness (8 new tests)
- [ ] Extend `ReviewControllerTest.java` — author PATCH/DELETE 200/403/422 paths (6 new tests)
- [ ] `sources/frontend/tests/e2e/reviews-author-edit.spec.ts` — owner edit/delete + sort dropdown
- [ ] `sources/frontend/tests/e2e/admin-reviews-moderation.spec.ts` — admin hide/unhide/hard-delete + filter dropdown
- [ ] V7 migration test fixture (seed 2 review rows in test profile) — verify partial UNIQUE allows re-review after soft-delete

## Manual UAT Checklist (cho `/gsd-verify-work`)

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
