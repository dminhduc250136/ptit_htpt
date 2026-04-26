---
phase: 08-cart-order-persistence
plan: "03"
subsystem: frontend
tags:
  - order-detail
  - checkout-redirect
  - types-update
  - css-modules
dependency_graph:
  requires:
    - 08-02  # backend getOrderById() endpoint + OrderItemDto với unitPrice/lineTotal
  provides:
    - checkout-redirect-to-order-detail
    - order-detail-real-fetch
    - productName-snapshot-D06
  affects:
    - sources/frontend/src/types/index.ts
    - sources/frontend/src/app/checkout/page.tsx
    - sources/frontend/src/app/profile/orders/[id]/page.tsx
    - sources/frontend/src/app/profile/orders/[id]/page.module.css
tech_stack:
  added: []
  patterns:
    - useCallback + useEffect async fetch pattern
    - CSS Modules với design tokens (var(--space-*), var(--text-body-*), var(--weight-*))
    - Null-safe field normalization (backend alias → UI fields)
key_files:
  created: []
  modified:
    - sources/frontend/src/types/index.ts
    - sources/frontend/src/app/checkout/page.tsx
    - sources/frontend/src/app/profile/orders/[id]/page.tsx
    - sources/frontend/src/app/profile/orders/[id]/page.module.css
decisions:
  - "D-06: truyền productName: item.name từ cart state vào CreateOrderRequest.items[] — backend persist snapshot"
  - "D-10: Order/OrderItem type có dual-field aliases (status/orderStatus, total/totalAmount, unitPrice/price, lineTotal/subtotal) để compat cả backend payload lẫn FE legacy"
  - "T-08-03-03/04: null-safe render — addr.filter(Boolean), order.items ?? []"
metrics:
  duration: "~25 minutes"
  completed: "2026-04-26T13:53:33Z"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 4
---

# Phase 08 Plan 03: FE Order Detail Wire + Checkout Redirect Summary

**One-liner:** Checkout redirect tới `/account/orders/{id}` sau đặt hàng thành công; order detail page render full breakdown thật từ `getOrderById()` với items table 4 cột và CSS design tokens.

---

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Cập nhật Order/OrderItem/CreateOrderRequest types + checkout redirect + productName | fca4c81 | types/index.ts, checkout/page.tsx |
| 2 | Order detail page — async fetch + full breakdown UI + CSS classes | 37b8569 | profile/orders/[id]/page.tsx, page.module.css |

---

## What Was Built

### Task 1 — Types update + checkout redirect

**`types/index.ts`:**
- `Order` interface: `orderCode` chuyển optional, thêm `status?`/`total?` aliases cho backend field names, `paymentStatus`/`orderStatus`/`subtotal`/`shippingFee`/`discount` chuyển optional
- `OrderItem` interface: `productImage` chuyển optional, thêm `unitPrice?` và `lineTotal?` (D-10 backend OrderItemDto aliases)
- `CreateOrderRequest.items[]`: thêm `productName: string` (D-06 contract fix — backend persist productName snapshot)

**`checkout/page.tsx`:**
- Xóa `showSuccess` state và toàn bộ `if (showSuccess)` block (successOverlay, successModal, successTitle, successDesc, successActions)
- Thêm `useRouter` from `next/navigation`; `router.push('/account/orders/' + order.id)` sau `clearCart()`
- Thêm `productName: i.name` vào `items.map()` khi build `createOrder()` body
- Giữ nguyên: stockModal, paymentModal, fieldErrors, bannerVisible, toàn bộ form JSX và 2 Modal components

### Task 2 — Order detail real fetch + full breakdown UI

**`profile/orders/[id]/page.tsx`** — viết lại hoàn toàn:
- Chuyển từ sync placeholder (`loadOrder()` trả undefined) → `'use client'` component với `useState` + `useEffect`
- `useCallback load()`: gọi `getOrderById(id)`, handle 3 states: loading / failed (5xx/network) / 404 (empty)
- Loading state: 4 skeleton rows + 2 skeleton cards (shimmer animation)
- Error state: `<RetrySection onRetry={load} loading={loading} />`
- Empty/404 state: "Mã đơn #{id} không tồn tại hoặc bạn không có quyền xem." (theo UI-SPEC §Empty States)
- Items table: `<table className={styles.itemsTable}>` với 4 columns (Sản phẩm / Số lượng / Đơn giá / Thành tiền)
- Normalize backend fields: `orderStatus ?? status`, `totalAmount ?? total`, `lineTotal ?? subtotal`, `unitPrice ?? price`
- Giữ nguyên: statusMap, paymentMethodMap, paymentStatusMap, tracker, priceBreakdown, infoColumn layout

**`profile/orders/[id]/page.module.css`** — thêm classes mới:
- `.itemsTable`, `.tableHeader`, `.tableHeaderCell`, `.tableRow`, `.tableCell`, `.lineTotalCell`
- `.skeletonRow`, `.skeletonCard` với `@keyframes shimmer`
- Tất cả classes dùng `var(--space-*)`, `var(--text-body-*)`, `var(--weight-*)`, `var(--outline-variant)` tokens

---

## Deviations from Plan

None — plan executed exactly as written.

---

## Threat Mitigations Applied

| Threat ID | Mitigation | Location |
|-----------|-----------|----------|
| T-08-03-03 | `[addr.street, addr.ward, addr.district, addr.city].filter(Boolean).join(', ')` | page.tsx line ~158 |
| T-08-03-04 | `(order.items ?? []).map(...)` | page.tsx line ~148 |

---

## Known Stubs

None — order detail page fetch real data từ `getOrderById()`. Checkout redirect tới real order ID từ API response. Không có hardcoded/placeholder data trong flow này.

---

## Self-Check

### Files exist:
- `sources/frontend/src/types/index.ts` — FOUND (modified)
- `sources/frontend/src/app/checkout/page.tsx` — FOUND (modified)
- `sources/frontend/src/app/profile/orders/[id]/page.tsx` — FOUND (rewritten)
- `sources/frontend/src/app/profile/orders/[id]/page.module.css` — FOUND (modified)

### Commits exist:
- `fca4c81` — feat(08-03): cập nhật Order types + checkout redirect + productName snapshot
- `37b8569` — feat(08-03): order detail page — async fetch + full breakdown UI + CSS table classes

## Self-Check: PASSED
