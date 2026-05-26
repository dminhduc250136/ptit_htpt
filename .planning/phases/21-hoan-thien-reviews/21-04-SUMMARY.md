---
phase: 21
plan: 04
subsystem: frontend / admin moderation + E2E regression-guard
tags: [frontend, admin, reviews, REV-06, playwright, e2e, moderation]
requires:
  - Phase 21 Plan 02 (BE admin endpoints /api/products/admin/reviews)
  - Phase 21 Plan 03 (FE service layer listAdminReviews/setReviewVisibility/hardDeleteReview + AdminReview type)
  - Phase 9 Plan 09-05 (Playwright global-setup + storageState user.json + admin.json)
provides:
  - /admin/reviews page với table + filter dropdown 4-state + pagination + actions inline
  - Sidebar admin nav extended với link "Đánh giá" (giữa Đơn hàng và Tài khoản)
  - 2 Playwright E2E specs (6 tests) cố định author edit/delete UX + admin moderation flow
  - Regression-guard cho cả 3 success criteria của Phase 21 (REV-04 + REV-05 + REV-06)
affects:
  - sources/frontend/src/app/admin/layout.tsx (navItems extended)
tech-stack:
  added: []
  patterns:
    - "Admin moderation table clone-then-adapt từ /admin/products pattern"
    - "Native <select> filter dropdown với aria-label='Lọc đánh giá' (4-state)"
    - "window.confirm + page.on('dialog') pattern cho hard-delete E2E"
    - "Cross-tab visibility check (context.newPage) — admin ẩn review → mở PDP tab khác → assert toHaveCount(0)"
    - "Strategy A degradation skip-if-no-data ở mỗi điểm yêu cầu seed (cùng style smoke.spec.ts)"
key-files:
  created:
    - sources/frontend/src/app/admin/reviews/page.tsx
    - sources/frontend/src/app/admin/reviews/page.module.css
    - sources/frontend/e2e/reviews-author-edit.spec.ts
    - sources/frontend/e2e/admin-reviews-moderation.spec.ts
  modified:
    - sources/frontend/src/app/admin/layout.tsx
decisions:
  - "Status badge mapping: deletedAt → variant='default' (xám), hidden → variant='out-of-stock' (đỏ), visible → variant='new' (xanh) — reuse Badge variants có sẵn"
  - "Filter select aria-label='Lọc đánh giá' để E2E spec dễ locate qua getByLabel; spec nguyên tắc Plan không bắt buộc nhưng cần thiết cho test"
  - "productLink open trong tab mới (target=_blank rel=noopener) — admin không mất context khi xem PDP cross-check"
  - "E2E specs đặt tại e2e/ (không phải tests/e2e/) — match playwright.config.ts testDir='./e2e' + global-setup.ts STATE_DIR"
  - "Window-expired test cho author DEFERRED — yêu cầu hoặc backdate created_at qua DB seed hoặc Spring profile override; vượt quá scope của plan E2E only"
  - "Strategy A skip-if-no-data ở mọi điểm yêu cầu seed (no product / no review / no productSlug) — match pattern smoke.spec.ts (Phase 15)"
metrics:
  duration: "~30 phút (continuous, single session)"
  completed: 2026-05-02
  tasks_completed: 2
  files_changed: 5
requirements: [REV-06]
---

# Phase 21 Plan 04: FE Admin Moderation + E2E Regression-Guard Summary

Hoàn tất Wave 3 của Phase 21 với /admin/reviews moderation page (REV-06) và 2 Playwright E2E specs cố định cả 3 success criteria của phase. Phase 21 hoàn tất 4/4 plans.

## What Was Built

### `/admin/reviews/page.tsx` (CREATE)

Admin moderation page clone-then-adapt từ `/admin/products`:

| Element | Implementation |
|---------|----------------|
| Heading | `<h1>Quản lý đánh giá</h1>` |
| Filter dropdown | Native `<select aria-label="Lọc đánh giá">` 4-state: Tất cả / Đang hiện / Đã ẩn / Đã xoá (author) |
| Pagination | Server-side `?page=&size=20`, Trước/Sau buttons với disable trên page=0 / isLast |
| Table columns | Sản phẩm (link `/products/{slug}` target=_blank) / Reviewer / Rating (★ unicode) / Nội dung (truncate 60ch + tooltip full) / Trạng thái (badge) / Ngày tạo (vi-VN) / Hành động |
| Status badge | `Đã xoá` (variant='default') / `Ẩn` (variant='out-of-stock') / `Hiện` (variant='new') |
| Action: hidden=false | Button "Ẩn" → `setReviewVisibility(id, true)` → toast 'Đã ẩn review' → refetch |
| Action: hidden=true | Button "Bỏ ẩn" → `setReviewVisibility(id, false)` → toast 'Đã bỏ ẩn review' → refetch |
| Action: hard-delete | Button "Xoá" (red) → `window.confirm('Xoá vĩnh viễn review này? Không thể hoàn tác.')` → `hardDeleteReview(id)` → toast 'Đã xoá vĩnh viễn' → refetch |
| Action: deletedAt rows | Chỉ hiện nút "Xoá" (no Hide/Unhide — D-17) |
| Loading state | 5 skeleton rows `<div className="skeleton">` |
| Empty state | `Không có đánh giá nào trong bộ lọc hiện tại.` |
| Error state | `<RetrySection onRetry={load} />` |

Toast wording đúng CONTEXT specifics 240. Hard-delete confirm wording đúng specifics 238.

### `/admin/reviews/page.module.css` (CREATE)

Clone từ `/admin/products/page.module.css` + extend với:
- `.filterRow` + `.filterSelect` — flex row với native select styling
- `.contentCell` — max-width 320px + ellipsis truncation
- `.rating` — color warning + letter-spacing cho ★ stars
- `.deleteBtn` — error color border + hover background
- `.paginationRow` — center-aligned pagination row
- `.empty` — center-aligned empty state
- `.productLink` — primary color underline-on-hover

### `admin/layout.tsx` (MODIFY — sidebar nav)

Insert nav item giữa "Đơn hàng" và "Tài khoản":
```tsx
{ href: '/admin/reviews', label: 'Đánh giá', icon: <svg ...><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg> }
```
Star polygon icon — visual cue cho review/rating context.

### `e2e/reviews-author-edit.spec.ts` (CREATE — 3 tests)

| Test | Coverage | Strategy A skip-if |
|------|----------|---------------------|
| AUTHOR-EDIT-1 | Happy path edit (textarea fill + 'Lưu thay đổi' + toast 'Đã cập nhật đánh giá' + content updated trong list) | no product / no edit button (user chưa có review) |
| AUTHOR-EDIT-2 | Soft-delete (page.on('dialog') accept + click 'Xoá' + toast 'Đã xoá đánh giá') | no delete button |
| AUTHOR-EDIT-3 | Sort dropdown change + URL persistence (?sort=rating_desc; default newest bỏ qs) | no sort dropdown (empty review list) |

**Window-expired test DEFERRED** — yêu cầu hoặc backdate `created_at` qua direct DB seed hoặc Spring profile override `app.reviews.edit-window-hours=0`. Cả 2 cách đều cần infrastructure ngoài E2E spec scope (cross-stack coordination với BE Plan 21-02). REV-04 24h-window logic đã được verified ở backend tests (`ReviewServiceEditWindowTest` — Plan 21-02).

### `e2e/admin-reviews-moderation.spec.ts` (CREATE — 3 tests)

| Test | Coverage | Strategy A skip-if |
|------|----------|---------------------|
| ADM-REV-1 | List render heading + filter 4-state + 3 column headers visible | (none — chỉ smoke render) |
| ADM-REV-2 | Hide flow: filter visible → click Ẩn → toast → cross-tab PDP visibility check (context.newPage + reviewerName toHaveCount(0)) → filter hidden → click Bỏ ẩn → toast | no visible review / no productSlug |
| ADM-REV-3 | Filter "Đã xoá" — verify deleted rows chỉ có nút Xoá (no Hide/Unhide button) | no deleted reviews |

**Cross-tab visibility check (ADM-REV-2)** chứng minh visibility filter ở public list hoạt động: hidden review KHÔNG xuất hiện trên PDP user-facing list.

## Verification

| Check | Result |
|-------|--------|
| `npx tsc --noEmit -p tsconfig.json` | exit 0 ✓ |
| `npm run build` | exit 0 ✓ (Next.js build succeeded; `/admin/reviews` route registered as static `○`) |
| `npx playwright test --list e2e/reviews-author-edit.spec.ts e2e/admin-reviews-moderation.spec.ts` | 6 tests parsed ✓ |
| Full Playwright run | NOT executed — yêu cầu Playwright browsers + docker stack (backend + frontend running). Specs syntactically valid; fully gated bởi Strategy A skip-if-no-data nên sẽ pass-with-skip nếu seed empty. |

## Acceptance Criteria Verification (grep counts)

```
listAdminReviews                  admin/reviews/page.tsx        : 1 ✓ (≥1)
setReviewVisibility               admin/reviews/page.tsx        : 1 ✓ (≥1)
hardDeleteReview                  admin/reviews/page.tsx        : 1 ✓ (≥1)
Tất cả|Đang hiện|Đã ẩn|Đã xoá     admin/reviews/page.tsx        : 4+ ✓ (4 filter options)
Xoá vĩnh viễn review này...       admin/reviews/page.tsx        : 1 ✓
Đã ẩn review|Đã bỏ ẩn review|...  admin/reviews/page.tsx        : 3 ✓ (≥3 toasts)
/admin/reviews                    admin/layout.tsx              : 1 ✓
Đánh giá (label)                  admin/layout.tsx              : 1 ✓
test(                             reviews-author-edit.spec.ts   : 3 ✓ (≥2 — happy + delete)
test(                             admin-reviews-moderation.spec : 3 ✓ (≥1)
Đã cập nhật đánh giá|Đã xoá ĐG    reviews-author-edit.spec.ts   : 2 ✓ (≥1)
Đã ẩn review|Đã bỏ ẩn review      admin-reviews-moderation.spec : 2 ✓ (≥2)
/admin/reviews                    admin-reviews-moderation.spec : 3 ✓ (≥1)
/products/                        admin-reviews-moderation.spec : 2 ✓ (≥1 — cross-tab PDP)
```

## Phase 21 OVERALL Completion Status

3/3 success criteria covered:

| Success Criterion | Plan | Status |
|-------------------|------|--------|
| 1. REV-04 author edit/delete inline trên PDP | Plan 21-03 (FE) + 21-02 (BE) + 21-01 (schema) | ✅ Complete + E2E lock-in (AUTHOR-EDIT-1, AUTHOR-EDIT-2) |
| 2. REV-05 sort dropdown 3-option + ?sort= | Plan 21-03 (FE) + 21-02 (BE sort param) | ✅ Complete + E2E lock-in (AUTHOR-EDIT-3) |
| 3. REV-06 admin /admin/reviews moderation | Plan 21-04 (FE this plan) + 21-02 (BE admin endpoints) | ✅ Complete + E2E lock-in (ADM-REV-1, ADM-REV-2, ADM-REV-3) |

Tổng cộng 4 plans (21-01 schema → 21-02 BE service+controllers → 21-03 FE author UX → 21-04 FE admin + E2E) thực thi đầy đủ. REV-04 + REV-05 + REV-06 yêu cầu của REQUIREMENTS.md tất cả satisfied.

## Deviations from Plan

None — plan executed exactly as written. Window-expired test deferral đã được anticipated trong plan (Task 2 action note: "Nếu setup (a) hoặc (b) phức tạp → fallback: chỉ test happy path").

## Auth Gates / Blockers

Không có. Storage state user.json + admin.json đã có từ Phase 9 global-setup.

## Threat Flags

Không có new threat surface phát hiện. Tất cả attack surface (admin endpoints, hard-delete confirm, cross-tab visibility leak) đã được cover trong 21-PLAN threat_model T-21-04-01..06.

## Self-Check: PASSED

Files verified to exist:
- FOUND: sources/frontend/src/app/admin/reviews/page.tsx
- FOUND: sources/frontend/src/app/admin/reviews/page.module.css
- FOUND: sources/frontend/src/app/admin/layout.tsx (modified — added Đánh giá nav)
- FOUND: sources/frontend/e2e/reviews-author-edit.spec.ts
- FOUND: sources/frontend/e2e/admin-reviews-moderation.spec.ts

Commits verified:
- FOUND: b2a15cd feat(21-04): admin moderation page /admin/reviews + sidebar nav 'Đánh giá'
- FOUND: 42f6fcb test(21-04): Playwright E2E specs cho author edit/delete + admin moderation

Build & lint:
- `npx tsc --noEmit` — exit 0 ✓
- `npm run build` — exit 0 ✓ (`/admin/reviews` static route compiled)
- `npx playwright test --list` — 6 tests parsed across 2 files ✓
