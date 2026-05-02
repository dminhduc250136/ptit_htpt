---
phase: 17-s-a-order-detail-items
plan: 02
subsystem: ui
tags: [frontend, nextjs, admin, order-detail, bug-fix, vietnamese-i18n]

requires:
  - phase: 17
    plan: 01
    provides: "@/lib/orderLabels + @/lib/useEnrichedItems"
provides:
  - "Admin order detail page (/admin/orders/[id]) render full items + shipping/payment thật"
affects: [17-03 user order detail extend (analog pattern), Phase 19 admin charts (reuse statusMap)]

tech-stack:
  added: []
  patterns:
    - "FE-side enrichment via useEnrichedItems hook (reuse Plan 17-01)"
    - "Single-source-of-truth labels via @/lib/orderLabels (statusMap + paymentMethodMap)"
    - "Inline-style admin pattern (KHÔNG CSS module) — consistency với 4 cards khác trong file"

key-files:
  created: []
  modified:
    - sources/frontend/src/app/admin/orders/[id]/page.tsx

key-decisions:
  - "D-02 implement: Xóa interface AdminOrder inline + cast 'as any' — getAdminOrderById đã trả Promise<Order>"
  - "D-03 implement: Giữ inline-style cardStyle/labelStyle — KHÔNG tạo CSS module mới"
  - "D-04 implement: Render shippingAddress 4 fields join `, ` + paymentMethodMap label hiển thị tiếng Việt"
  - "D-05 implement: Empty state graceful '<p>Đơn hàng không có sản phẩm</p>' (KHÔNG render table header trống)"
  - "D-06 implement: next/image 64×64 + fallback <div> 64×64 với 📦 emoji"
  - "Pragmatic commit: Task 1 + Task 2 cùng commit vì 2 task touch cùng file ở vùng overlap (statusMap replace spans cả 2 task)"

requirements-completed: [ADMIN-06]

duration: 3min
completed: 2026-05-02
---

# Phase 17 Plan 02: Admin Order Detail Items Render Summary

**Rewrite admin order detail page (`/admin/orders/[id]`) — xóa placeholder hardcoded "khả dụng sau khi Phase 8" + render full items table 4 cột với thumbnail/brand + shipping address + payment method tiếng Việt qua `useEnrichedItems` hook + `@/lib/orderLabels` từ Plan 17-01.**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-05-02T14:35:59Z
- **Completed:** 2026-05-02T14:38:47Z
- **Tasks:** 2 (committed as 1 — pragmatic, see Deviations)
- **Files modified:** 1

## Accomplishments

- **Type safety**: Xóa `interface AdminOrder` inline (10 dòng) + xóa `as any` cast — switch sang `Order` từ `@/types`. tsc + lint pass.
- **Shipping card**: Render `order.shippingAddress` (street/ward/district/city join `, `) + `paymentMethodMap[order.paymentMethod]` thay vì hardcoded `"—"`.
- **Items table 4 cột**: Sản phẩm (thumbnail 64×64 + name + brand subtitle) / Số lượng / Đơn giá / Thành tiền — render qua `useEnrichedItems(order?.items)` để enrich `thumbnailUrl + brand` từ product-svc.
- **Empty state**: `<p>Đơn hàng không có sản phẩm</p>` khi `enriched.length === 0` (D-05) — KHÔNG render table header trống.
- **Image fallback**: Khi `thumbnailUrl` null → `<div>` 64×64 với background `var(--surface-container-high)` + emoji 📦 (D-06).
- **Status labels DRY**: Replace local `STATUS_LABELS` + `STATUS_VARIANTS` inline maps bằng `statusMap[currentStatus]?.label` / `?.variant` từ `@/lib/orderLabels` — single-source-of-truth giữa admin và user pages.
- **Null-safe aliases**: `order.status ?? order.orderStatus ?? 'PENDING'`, `order.totalAmount ?? order.total ?? 0`, `it.unitPrice ?? it.price ?? 0`, `it.lineTotal ?? it.subtotal ?? 0` (Pattern C).

## Task Commits

1. **Task 1+2 (combined): rewrite admin order detail page with real items + shipping** — `a54abd4` (feat)

Note: Plan tách 2 task nhưng commit gộp vì cả hai task modify cùng file admin/orders/[id]/page.tsx ở các vùng overlap (đặc biệt statusMap replace yêu cầu touch các reference cross-cut). Tách commit theo logical scope sẽ tạo intermediate state non-compileable. Chọn pragmatic single commit (Rule 3 — commit boundary follow logical atomicity).

## Files Created/Modified

- `sources/frontend/src/app/admin/orders/[id]/page.tsx` (MODIFIED, +90 / -40) — full rewrite render flow.

## Verification Status

- `npx tsc --noEmit` → **PASS** (exit 0).
- `npm run lint` → **PASS exit 0** cho file admin/orders/[id]/page.tsx (0 errors trong file mới sửa). Pre-existing lint issues trong `admin/page.tsx:59` + `AddressPicker.tsx:39` (`react-hooks/set-state-in-effect`) đã được Plan 17-01 ghi nhận trong `deferred-items.md` — out of scope per Scope Boundary rule.
- Acceptance grep checks (10/10 PASS):
  - `interface AdminOrder` count = 0 ✓
  - `as any` count = 0 ✓
  - `import type { Order }` match ✓
  - `useState<Order` match ✓
  - `from '@/lib/orderLabels'` match ✓
  - `from '@/lib/useEnrichedItems'` match ✓
  - `khả dụng sau khi Phase 8` count = 0 ✓
  - `Đơn hàng không có sản phẩm` match (line 138) ✓
  - `useEnrichedItems(order?.items)` match (line 62) ✓
  - `<Image` count ≥ 1 ✓
  - `paymentMethodMap[order.paymentMethod]` match ✓
  - `shippingAddress.street` match (line 120) ✓
  - `Thông tin giao hàng` match ✓
  - `<thead>` + `<tbody>` count = 7 (≥ 2) ✓

## Decisions Made

- **`useEnrichedItems(order?.items)`** trước early return (line 62) — hooks luôn được call ở top-level cùng thứ tự (React hooks rules). Hook accept `undefined` và return `[]` khi `items` null — safe trong skeleton/error states.
- **`currentStatus` derived const** thay vì `order?.status ?? ...` lặp lại — DRY giữa Badge render + select disabled check.
- **Pragmatic commit boundary** — gộp Task 1 + Task 2 thành 1 commit vì coupling chặt qua statusMap cross-cut replace.

## Deviations from Plan

### Pragmatic — Commit Granularity

**[Rule 3 - Commit boundary] Gộp Task 1 + Task 2 thành 1 commit `a54abd4`**
- **Found during:** Task 1 implementation
- **Issue:** Task 1 step 3 (replace STATUS_LABELS/STATUS_VARIANTS với statusMap) yêu cầu touch các reference span cả vùng render code mà Task 2 sẽ rewrite. Tách commit gây intermediate state non-compileable hoặc duplicate code.
- **Fix:** Combined single commit với message reflect cả type refactor + render rewrite.
- **Files modified:** sources/frontend/src/app/admin/orders/[id]/page.tsx
- **Commit:** `a54abd4`

Không có deviation Rule 1/2/4 — KHÔNG bug pre-existing nào surface, KHÔNG missing critical functionality, KHÔNG architectural change.

## Visual Check / UAT

**Manual UAT pending** (Wave 3 plan 17-04 sẽ E2E gate qua Playwright):
- URL: `/admin/orders/{orderId}` (cần admin role)
- Expected:
  - Card "Thông tin giao hàng" hiển thị địa chỉ thật (street, ward, district, city) + payment method tiếng Việt (e.g. "Thanh toán khi nhận hàng" cho COD).
  - Card "Sản phẩm" hiển thị table 4 cột với thumbnail 64×64 + brand subtitle. Tổng cộng align right.
  - Empty items: hiển thị "Đơn hàng không có sản phẩm" thay vì table rỗng.
  - Soft-deleted product: thumbnail fallback emoji 📦 + brand "—" (Promise.allSettled không kill render).
- KHÔNG còn placeholder "khả dụng sau khi Phase 8 hoàn thiện" anywhere.

## Issues Encountered

Không có. Implementation thuần follow plan.

## Self-Check: PASSED

**Files:**
- FOUND: sources/frontend/src/app/admin/orders/[id]/page.tsx (modified)

**Commits:**
- FOUND: a54abd4 (Task 1+2 combined)

## Next Phase Readiness

**Wave 2 plan 17-03 ready:** Pattern admin đã verified — Plan 17-03 user-side analog có thể follow cùng `useEnrichedItems` + `paymentMethodMap` import pattern (file khác: `profile/orders/[id]/page.tsx`, KHÔNG đụng admin file đã commit).

**Blockers:** None.

---
*Phase: 17-s-a-order-detail-items*
*Completed: 2026-05-02*
