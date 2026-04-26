---
phase: "08"
plan: "04"
subsystem: frontend
tags: [stock-display, add-to-cart, disabled-fix, css-tokens, wcag]
dependency_graph:
  requires: [08-01]
  provides: [product-detail-stock-wire]
  affects: [products/[slug]/page.tsx, products/[slug]/page.module.css]
tech_stack:
  added: []
  patterns: [CSS custom properties, React useState async handler, WCAG 2.5.5 touch target]
key_files:
  created: []
  modified:
    - sources/frontend/src/app/products/[slug]/page.tsx
    - sources/frontend/src/app/products/[slug]/page.module.css
decisions:
  - "Button disabled chỉ dùng product.stock === 0 (không còn ?? 0 wrapper thừa)"
  - "Quantity + button dùng product.stock || 1 thay vì ?? 99 để giới hạn đúng theo stock thật"
  - "addingToCart state wrap onClick async để hiện loading spinner khi user click"
  - "CSS .inStock dùng var(--secondary-container) thay hardcode #16a34a theo UI-SPEC"
metrics:
  duration: "~15 minutes"
  completed_date: "2026-04-26"
  tasks_completed: 1
  files_changed: 2
---

# Phase 08 Plan 04: Product Detail Stock Wire + Disabled Fix Summary

**One-liner:** Fix product detail page — wire `product.stock` thật, sửa disabled logic add-to-cart button, thêm addingToCart loading state, chuẩn hóa CSS stock classes theo UI-SPEC tokens.

---

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Diagnose + fix disabled bug + addingToCart state + CSS classes | 41a3bba | page.tsx, page.module.css |

---

## What Was Built

### Task 1: Fix product detail disabled logic + CSS tokens

**`sources/frontend/src/app/products/[slug]/page.tsx`**

- Thêm `const [addingToCart, setAddingToCart] = useState(false)` — loading state cho add-to-cart
- Fix add-to-cart Button: `disabled={product.stock === 0}` (trước là `disabled={(product.stock ?? 0) === 0}` — logic tương đương nhưng rõ ràng hơn sau Plan 08-01 stock thật)
- Thêm `loading={addingToCart}` prop vào Button — hiển thị spinner khi đang xử lý
- Button label: `{product.stock === 0 ? 'Hết hàng' : 'Thêm vào giỏ hàng'}` (UI-SPEC §Add-to-Cart Button States)
- onClick chuyển thành async với try/catch/finally — set addingToCart true/false, toast error nếu thất bại
- Quantity + button: `Math.min(product.stock || 1, quantity + 1)` và `disabled={quantity >= (product.stock || 1)}` — bỏ `?? 99` fallback (D-15: stock thật từ Plan 08-01 backend)

**`sources/frontend/src/app/products/[slug]/page.module.css`**

- `.inStock`: `color: var(--secondary-container)` (bỏ hardcode `#16a34a`), `font-size: var(--text-body-sm)`, `font-weight: var(--weight-semibold)`, thêm `line-height: var(--leading-normal)` — theo UI-SPEC §CSS Modules Contract
- `.outOfStock`: `color: var(--error)` (đúng), `font-size: var(--text-body-sm)`, `font-weight: var(--weight-semibold)`, thêm `line-height: var(--leading-normal)`
- `.qtyBtn`: thêm `min-width: 44px; min-height: 44px` (WCAG 2.5.5 touch target), tăng width từ 40px lên 44px

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] CSS .inStock dùng hardcode hex #16a34a thay vì var(--secondary-container)**
- **Found during:** Task 1 — đọc page.module.css
- **Issue:** `.inStock` dùng `color: #16a34a` (green) thay vì `var(--secondary-container)` (orange #fe6b00) theo UI-SPEC. Đây là sai token — "Còn hàng" phải màu orange (brand accent), không phải green
- **Fix:** Thay `#16a34a` → `var(--secondary-container)`
- **Files modified:** sources/frontend/src/app/products/[slug]/page.module.css
- **Commit:** 41a3bba

**2. [Rule 1 - Bug] CSS .inStock và .outOfStock dùng var(--weight-medium) thay vì var(--weight-semibold)**
- **Found during:** Task 1 — đọc page.module.css
- **Issue:** UI-SPEC Typography: "Label / badge / meta" dùng `var(--weight-semibold)` 600. Plan 04 acceptance criteria yêu cầu weight-semibold
- **Fix:** Thay `var(--weight-medium)` → `var(--weight-semibold)` trên cả 2 classes
- **Files modified:** sources/frontend/src/app/products/[slug]/page.module.css
- **Commit:** 41a3bba

**3. [Rule 1 - Bug] CSS .inStock và .outOfStock dùng font-size: var(--text-body-md) thay vì var(--text-body-sm)**
- **Found during:** Task 1 — đọc page.module.css
- **Issue:** UI-SPEC §CSS Modules Contract quy định `.inStock` và `.outOfStock` dùng `var(--text-body-sm)` (12px)
- **Fix:** Thay `var(--text-body-md)` → `var(--text-body-sm)` trên cả 2 classes
- **Files modified:** sources/frontend/src/app/products/[slug]/page.module.css
- **Commit:** 41a3bba

---

## Known Stubs

Không có stub. Stock display wire thật từ `product.stock` (Plan 08-01 đã fix backend trả stock thật).

---

## Threat Surface Scan

Không có surface mới ngoài threat model. Plan đã khai báo T-08-04-01 (quantity bypass via DOM — accept) và T-08-04-03 (addToCart với stock=0 bypass — backend validate via D-04 Plan 08-02).

---

## Self-Check: PASSED

- FOUND: sources/frontend/src/app/products/[slug]/page.tsx
- FOUND: sources/frontend/src/app/products/[slug]/page.module.css
- FOUND: .planning/phases/08-cart-order-persistence/08-04-SUMMARY.md
- FOUND commit: 41a3bba fix(08-04): wire product.stock thật + fix disabled logic + CSS tokens
