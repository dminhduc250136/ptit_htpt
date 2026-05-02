---
phase: 17-s-a-order-detail-items
plan: 03
subsystem: ui
tags: [frontend, nextjs, user, order-detail, image, vietnamese-i18n]

requires:
  - phase: 17
    plan: 01
    provides: "@/lib/orderLabels + @/lib/useEnrichedItems"
provides:
  - "User order detail page (/profile/orders/[id]) render full items với thumbnail 64x64 + brand"
affects: [Phase 17 Plan 04 (E2E gate Wave 3 sẽ assert visible behavior)]

tech-stack:
  added: []
  patterns:
    - "FE-side enrichment via useEnrichedItems hook (reuse Plan 17-01)"
    - "Single-source-of-truth labels via @/lib/orderLabels (statusMap + paymentMethodMap + paymentStatusMap)"
    - "CSS module pattern (user page) — append classes vào cuối file, KHÔNG sửa class cũ"

key-files:
  created: []
  modified:
    - sources/frontend/src/app/profile/orders/[id]/page.tsx
    - sources/frontend/src/app/profile/orders/[id]/page.module.css

key-decisions:
  - "D-01 implement: useEnrichedItems(order?.items) — hook accept undefined trả [] safe trong skeleton"
  - "D-05 implement: Empty state '<p>Đơn hàng không có sản phẩm</p>' wrap toàn bộ <table> trong ternary"
  - "D-06 implement: <Image> 64x64 + fallback <div> 64x64 emoji 📦"
  - "Giữ formatPrice helper (đã dùng nhất quán trong file) thay vì toLocaleString"

requirements-completed: [ORDER-01]

duration: 2min
completed: 2026-05-02
---

# Phase 17 Plan 03: User Order Detail Items Extend Summary

**Extend trang `/profile/orders/[id]` — items table thêm thumbnail 64×64 + brand subtitle qua `useEnrichedItems` hook + replace 3 inline label maps bằng import từ `@/lib/orderLabels` (DRY với admin Plan 02). User cuối cùng nhìn thấy ảnh + thương hiệu cho mỗi sản phẩm đã mua trong đơn hàng.**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-05-02T14:41:58Z
- **Completed:** 2026-05-02T14:43:55Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- **Imports DRY**: Xóa 3 inline `const statusMap/paymentMethodMap/paymentStatusMap` (lines 13-32 cũ, ~20 dòng) → import từ `@/lib/orderLabels`. Tên reference giữ nguyên — không phải đổi sites khác trong file.
- **Hook wired**: `const enriched = useEnrichedItems(order?.items)` ở top-level (line 22) — call trước early returns để tuân React hooks rules.
- **Items table cell "Sản phẩm"**: Wrap render trong `enriched.length === 0 ? <p>...</p> : <table>...` (D-05). Mỗi row có `<div className={styles.itemCellInner}>` chứa `<Image width={64} height={64}>` (hoặc fallback `<div className={styles.itemThumbPlaceholder}>📦</div>`) + name/brand stack bên phải.
- **Brand fallback**: `item.brand ?? '—'` (Pitfall #5 — soft-deleted product không kill render).
- **CSS module**: Append 4 class mới vào CUỐI file (`.itemCellInner` flex container, `.itemThumb` 64×64 object-fit:cover, `.itemThumbPlaceholder` 64×64 với surface-container-high background, `.itemBrand` text-body-sm subtitle). KHÔNG sửa `.itemImg`/`.tableCell`/`.itemsTable`/`.itemName`/`.lineTotalCell`/`.tableRow` cũ.

## Task Commits

1. **Task 1: Update imports + wire useEnrichedItems + extend items table cell** — `ff9fca0` (feat)
2. **Task 2: Append CSS classes cho thumbnail cell + brand subtitle** — `c9946bf` (feat)

## Files Created/Modified

- `sources/frontend/src/app/profile/orders/[id]/page.tsx` (MODIFIED, +53 / -47) — imports refactor + hook + items table rewrite.
- `sources/frontend/src/app/profile/orders/[id]/page.module.css` (MODIFIED, +34 / -0) — 4 class mới appended.

## Verification Status

- `npx tsc --noEmit` → **PASS** (exit 0, no type errors).
- `npm run lint` → **0 errors trong 2 file đã sửa** (`profile/orders/[id]/page.tsx` + `page.module.css`). Pre-existing 2 errors trong `admin/page.tsx:59` + `AddressPicker.tsx:39` (`react-hooks/set-state-in-effect`) đã được Plan 17-01 ghi nhận trong `deferred-items.md` — out of scope per Scope Boundary rule.
- Acceptance grep checks (8/8 PASS):
  - `^const statusMap` count = 0 ✓ (inline removed)
  - `^const paymentMethodMap` count = 0 ✓
  - `^const paymentStatusMap` count = 0 ✓
  - `from '@/lib/orderLabels'` match ✓
  - `useEnrichedItems(order?.items)` match ✓
  - `<Image` count ≥ 1 ✓
  - `Đơn hàng không có sản phẩm` match ✓
  - `itemCellInner` + `itemBrand` match ✓
- CSS acceptance (6/6 PASS):
  - `.itemCellInner`, `.itemThumb`, `.itemThumbPlaceholder`, `.itemBrand` — 4 classes mới có ✓
  - `.itemImg` existing class still present ✓
  - `var(--surface-container-high)` token present (D-06) ✓

## Decisions Made

- **`useEnrichedItems(order?.items)` ở top-level** trước early returns (loading/failed/!order) — React hooks rules. Hook accept `undefined` và return `[]` safe.
- **Giữ `formatPrice(unitPrice)`** thay vì `unitPrice.toLocaleString('vi-VN') + '₫'` — pattern hiện tại file dùng formatPrice nhất quán (line 188-204 priceBreakdown). Plan có note Step 4 cho phép.
- **Empty state wrap toàn bộ table** trong ternary `enriched.length === 0 ? <p>...</p> : <table>...` — gọn hơn render `<thead>` + empty `<tbody>`.

## Deviations from Plan

None — plan executed exactly as written.

Pre-existing lint errors trong `admin/page.tsx` + `AddressPicker.tsx` (đã ghi nhận Plan 17-01 deferred-items.md) — KHÔNG touch per Scope Boundary rule.

## Visual Check / UAT

**Manual UAT pending** (Wave 3 plan 17-04 sẽ E2E gate qua Playwright):
- URL: `/profile/orders/{orderId}` (cần user login)
- Expected:
  - Section "Sản phẩm" hiển thị table 4 cột với thumbnail 64×64 bên trái mỗi row + brand subtitle dưới productName.
  - Brand "—" khi product fetch fail (soft-deleted).
  - Thumbnail fallback emoji 📦 khi `thumbnailUrl` null.
  - Empty items: hiển thị "Đơn hàng không có sản phẩm" thay vì table rỗng.
  - Status tracker, shipping address, payment, price breakdown vẫn hoạt động (KHÔNG sửa).

**Pending UAT cases (theo plan output spec):**
- Empty items render text "Đơn hàng không có sản phẩm" (chưa có order legacy items=[] để test live).
- Soft-deleted product: brand fallback "—" + thumbnail placeholder (cần admin xóa product để repro).

## Issues Encountered

Không có. Implementation thuần follow plan.

## Self-Check: PASSED

**Files:**
- FOUND: sources/frontend/src/app/profile/orders/[id]/page.tsx (modified)
- FOUND: sources/frontend/src/app/profile/orders/[id]/page.module.css (modified)

**Commits:**
- FOUND: ff9fca0 (Task 1 — page.tsx)
- FOUND: c9946bf (Task 2 — page.module.css)

## Next Phase Readiness

**Wave 3 ready:** Cả 3 plan của Wave 1+2 đã merge. Plan 17-04 (E2E gate) có thể assert browser-level cho cả `/admin/orders/[id]` và `/profile/orders/[id]` — pattern thumbnail+brand visual consistent (cùng 64×64).

**Blockers:** None.

---
*Phase: 17-s-a-order-detail-items*
*Completed: 2026-05-02*
