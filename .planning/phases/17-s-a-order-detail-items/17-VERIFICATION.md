---
phase: 17-s-a-order-detail-items
verified: 2026-05-02T15:00:00Z
status: human_needed
score: 9/9 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Mở /profile/orders/{id} bằng user account và xác nhận items table render thumbnail 64×64 + brand subtitle + name + qty + đơn giá + thành tiền"
    expected: "Mỗi row hiển thị Image (hoặc placeholder 📦) + brand (hoặc '—') — KHÔNG còn placeholder, KHÔNG empty rows"
    why_human: "Visual rendering + Promise.allSettled async fetch enrichment cần browser thật để xác nhận thumbnail load + brand merge đúng"
  - test: "Mở /admin/orders/{id} bằng admin account và xác nhận card 'Sản phẩm' render full items table"
    expected: "Items table 4 cột render đúng + card 'Thông tin giao hàng' render địa chỉ + payment method tiếng Việt — KHÔNG còn 'khả dụng sau khi Phase 8'"
    why_human: "Visual UAT — backend microservices không chạy local nên không thể spin up + run E2E tự động"
  - test: "Empty items state — visit order legacy có items=[] (hoặc force devtools)"
    expected: "Cả 2 page render '<p>Đơn hàng không có sản phẩm</p>' thay vì table rỗng"
    why_human: "Cần seed data hoặc devtools manipulation, không grep được"
  - test: "Soft-deleted product fallback — admin soft-delete 1 product → visit order chứa product đó"
    expected: "Thumbnail = placeholder div 📦, brand subtitle = '—' (Promise.allSettled không kill render)"
    why_human: "Cần manipulate DB state để test fallback behavior"
  - test: "Playwright suite full run sau khi backend stack up"
    expected: "`npx playwright test e2e/admin-orders.spec.ts e2e/order-detail.spec.ts` → 5 tests pass hoặc skip-with-reason, 0 failed"
    why_human: "Backend microservices (gateway, user-svc, postgres) không chạy local → global-setup login fail. Cần infra ready để run E2E"
---

# Phase 17: Admin & User Order Detail Items Verification Report

**Phase Goal:** Người dùng và admin xem chi tiết đơn hàng thấy đầy đủ danh sách sản phẩm đã mua thay vì placeholder text.
**Verified:** 2026-05-02
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #   | Truth | Status | Evidence |
| --- | ----- | ------ | -------- |
| 1 | `/profile/orders/[id]` hiển thị danh sách line items với ảnh / tên / brand / đơn giá / qty / thành tiền — KHÔNG placeholder | ✓ VERIFIED | `profile/orders/[id]/page.tsx:135-183` render `<table>` 4 cột + `<Image width={64} height={64}>` + brand subtitle + `useEnrichedItems(order?.items)` ở line 22 |
| 2 | `/admin/orders/[id]` đúng danh sách sản phẩm — KHÔNG còn "Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện" | ✓ VERIFIED | `admin/orders/[id]/page.tsx:135-202` render full items table; grep "khả dụng sau khi Phase 8" trên toàn `src/` → 0 match |
| 3 | AdminOrder TypeScript interface có `items: OrderItem[]`; FE parse `ApiResponse<OrderDto>` unwrap đúng | ✓ VERIFIED | `admin/orders/[id]/page.tsx:11` `import type { Order } from '@/types'` (Order đã có `items?: OrderItem[]`); inline `interface AdminOrder` xóa hoàn toàn ở detail page; `as any` xóa hoàn toàn ở detail page |
| 4 | File `@/lib/orderLabels.ts` export 3 maps Vietnamese | ✓ VERIFIED | `lib/orderLabels.ts` export `statusMap`, `paymentMethodMap`, `paymentStatusMap` với labels verbatim |
| 5 | File `@/lib/useEnrichedItems.ts` export hook với Promise.allSettled + cancelled cleanup | ✓ VERIFIED | `lib/useEnrichedItems.ts:25` `Promise.allSettled`, line 24 `let cancelled = false`, line 38 cleanup, line 23 `new Set(items.map(...))` dedup |
| 6 | Admin page render shipping address + payment method tiếng Việt | ✓ VERIFIED | `admin/orders/[id]/page.tsx:117-128` render `shippingAddress.{street,ward,district,city}.filter(Boolean).join(', ')` + `paymentMethodMap[order.paymentMethod]` |
| 7 | Empty items state graceful (cả 2 page) | ✓ VERIFIED | Admin line 137-138 + User line 136-137 cùng render `<p>Đơn hàng không có sản phẩm</p>` |
| 8 | CSS module có 4 classes mới cho thumbnail cell + brand subtitle | ✓ VERIFIED | `profile/orders/[id]/page.module.css:132,138,147,159` — `.itemCellInner`, `.itemThumb`, `.itemThumbPlaceholder`, `.itemBrand` |
| 9 | E2E specs có ADM-ORD-3 (no-placeholder + headings) + ORD-DTL-2 extend (table rows) | ✓ VERIFIED | `e2e/admin-orders.spec.ts:62` test ADM-ORD-3 + line 77 `toHaveCount(0)`; `e2e/order-detail.spec.ts:75-79` rowCount assertion |

**Score:** 9/9 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `sources/frontend/src/lib/orderLabels.ts` | 3 Vietnamese label maps | ✓ VERIFIED | Exists, 27 lines, 3 export const, imported by both pages |
| `sources/frontend/src/lib/useEnrichedItems.ts` | Hook + EnrichedItem type | ✓ VERIFIED | Exists, 42 lines, Promise.allSettled + cleanup flag, imported by both pages |
| `sources/frontend/src/app/admin/orders/[id]/page.tsx` | Admin order detail rewrite | ✓ VERIFIED | Modified, full render flow, 0 placeholder, 0 `as any` |
| `sources/frontend/src/app/profile/orders/[id]/page.tsx` | User order detail extend | ✓ VERIFIED | Modified, items table với thumbnail + brand subtitle |
| `sources/frontend/src/app/profile/orders/[id]/page.module.css` | 4 CSS classes mới appended | ✓ VERIFIED | Modified, classes verified ở lines 132/138/147/159 |
| `sources/frontend/e2e/admin-orders.spec.ts` | ADM-ORD-3 test added | ✓ VERIFIED | Modified, test ADM-ORD-3 ở line 62 |
| `sources/frontend/e2e/order-detail.spec.ts` | ORD-DTL-2 extend with rowCount | ✓ VERIFIED | Modified, Phase 17 comment + rowCount assertion ở lines 75-79 |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| `admin/orders/[id]/page.tsx` | `lib/useEnrichedItems.ts` | `useEnrichedItems(order?.items)` | ✓ WIRED | line 13 import + line 62 invocation |
| `admin/orders/[id]/page.tsx` | `lib/orderLabels.ts` | `import { paymentMethodMap, statusMap }` | ✓ WIRED | line 12 import + line 110 + line 128 + line 212 usage |
| `admin/orders/[id]/page.tsx` | `@/types` | `import type { Order }` | ✓ WIRED | line 11 + line 23 `useState<Order | null>` |
| `profile/orders/[id]/page.tsx` | `lib/useEnrichedItems.ts` | `useEnrichedItems(order?.items)` | ✓ WIRED | line 13 import + line 22 invocation |
| `profile/orders/[id]/page.tsx` | `lib/orderLabels.ts` | `import { statusMap, paymentMethodMap, paymentStatusMap }` | ✓ WIRED | line 12 import + line 121 + line 218 + line 220 usage |
| `lib/useEnrichedItems.ts` | `services/products.ts (getProductById)` | `Promise.allSettled mapping` | ✓ WIRED | line 8 import + line 25 `Promise.allSettled(uniqueIds.map(id => getProductById(id)))` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| `admin/orders/[id]/page.tsx` | `order.items` | `getAdminOrderById(id)` từ `services/orders.ts` (BE order-svc, đã ship Phase 8) | ✓ Yes — BE đã có `findByIdWithItems()` per RESEARCH | ✓ FLOWING |
| `admin/orders/[id]/page.tsx` | `enriched[].thumbnailUrl/brand` | `useEnrichedItems` → `getProductById` (BE product-svc) | ✓ Yes — fetch real product data parallel | ✓ FLOWING |
| `profile/orders/[id]/page.tsx` | `order.items` | `getOrderById(id)` từ `services/orders.ts` | ✓ Yes — same BE endpoint | ✓ FLOWING |
| `profile/orders/[id]/page.tsx` | `enriched[].thumbnailUrl/brand` | `useEnrichedItems` → `getProductById` | ✓ Yes — same enrichment hook | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| TypeScript compile | `npx tsc --noEmit` (per SUMMARY 17-04) | exit 0 | ✓ PASS (per SUMMARY) |
| Playwright E2E | `npx playwright test e2e/admin-orders.spec.ts e2e/order-detail.spec.ts` | global-setup timeout — backend microservices không chạy local | ? SKIP (infra prerequisite — routed to human verification) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| ORDER-01 | 17-01, 17-03, 17-04 | User `/profile/orders/[id]` hiển thị full line items + verify BE/FE DTO mapping | ✓ SATISFIED | `profile/orders/[id]/page.tsx` render thumbnail + brand + name + qty + price + lineTotal qua hook; ORD-DTL-2 e2e extend rowCount assertion |
| ADMIN-06 | 17-01, 17-02, 17-04 | Admin `/admin/orders/[id]` hiển thị full line items, fix interface AdminOrder + items[] | ✓ SATISFIED | `admin/orders/[id]/page.tsx` xóa inline AdminOrder + `as any`, switch sang `Order` type với `items: OrderItem[]`; render full table; placeholder "khả dụng sau khi Phase 8" hoàn toàn xóa; ADM-ORD-3 e2e regression-guard added |

Không có orphaned requirement — REQUIREMENTS.md mapping (line 137-138) đúng 2 ID `ORDER-01 + ADMIN-06` → tất cả declared trong PLAN frontmatter.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| `src/app/admin/orders/page.tsx` | 13 | `interface AdminOrder` (list page, KHÔNG phải detail page) | ℹ️ Info | Out of scope Phase 17 — phase chỉ scope detail page. Nằm trong `deferred-items.md` (đã ghi nhận Plan 17-01 SUMMARY) |
| `src/app/admin/orders/page.tsx` | 52 | `as any[]` cast | ℹ️ Info | Out of scope Phase 17 — đã document defer |

Detail page `/admin/orders/[id]` đã hoàn toàn clean: 0 `interface AdminOrder`, 0 `as any`. List page (`/admin/orders`) là code legacy ngoài scope.

### Human Verification Required

Xem frontmatter `human_verification` — 5 items pending UAT:

1. **User order detail visual UAT** — render thumbnail + brand thật.
2. **Admin order detail visual UAT** — full table + shipping address card.
3. **Empty items edge case** — graceful empty state cả 2 page.
4. **Soft-deleted product fallback** — placeholder + "—" brand.
5. **Playwright E2E full run** — sau khi backend stack up.

### Gaps Summary

KHÔNG có gap blocking goal achievement. Toàn bộ codebase changes verified bằng Read + Grep:
- 9/9 observable truths VERIFIED
- 7/7 artifacts exist + substantive + wired
- 6/6 key links WIRED
- 4/4 data flows FLOWING
- 2/2 requirements (ORDER-01, ADMIN-06) SATISFIED
- TypeScript compile pass (per Plan summaries)
- E2E spec compile + grep checks pass (Playwright run deferred do infra prerequisite, KHÔNG phải gap code-level)

Status `human_needed` thay vì `passed` vì: (1) visual UAT bắt buộc cho UI changes (visible-first project priority); (2) Playwright E2E chưa chạy được local → cần manual UAT cover regression cho ADM-ORD-3 + ORD-DTL-2; (3) 2 edge case (empty items + soft-deleted product) chỉ kiểm chứng bằng manipulation thật.

---

_Verified: 2026-05-02_
_Verifier: Claude (gsd-verifier)_
