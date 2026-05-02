# Phase 17: Sửa Order Detail Items — Research

**Researched:** 2026-05-02
**Domain:** Frontend bug-fix (Next.js 16 App Router) + FE-side data enrichment
**Confidence:** HIGH (toàn bộ findings verified bằng codebase grep + file inspection trực tiếp)

## Summary

Phase 17 là bug-fix thuần FE. Backend đã sẵn sàng từ Phase 8: `OrderDto` chứa `items: List<OrderItemDto>` + `shippingAddress` (JSONB Map) + `paymentMethod`; `OrderCrudService.getOrder()` dùng `findByIdWithItems()` với `@Transactional(readOnly=true)` → eager fetch-join, KHÔNG có vấn đề LazyInitialization hay N+1 phía BE. `httpGet` đã auto-unwrap `ApiResponse` envelope (services/http.ts:108).

Vấn đề chính nằm ở 2 file FE:
1. `admin/orders/[id]/page.tsx` line 145 hardcode placeholder `"Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện"` + interface `AdminOrder` inline (lines 13-22) thiếu `items / shippingAddress / paymentMethod` → buộc dùng `as any` (line 58).
2. `profile/orders/[id]/page.tsx` đã render items table nhưng thiếu **image + brand** (success criteria 1).

Enrichment image+brand thực hiện FE-side qua `Promise.allSettled([getProductById(id), ...])` — KHÔNG sửa BE/DB (D-01).

**Primary recommendation:** Tạo helper hook/util `useEnrichedItems(items)` dùng chung cho cả admin + user page. Extract `paymentMethodMap`/`statusMap` sang `@/lib/orderLabels.ts`. Replace `AdminOrder` inline interface bằng import `Order` từ `@/types`. Render items qua `next/image` 64×64 với fallback placeholder.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01** — Source image+brand: FE-side enrichment qua `Promise.allSettled([getProductById(productId), ...])` cho mỗi unique productId. Cache trong `useMemo` theo `order.id`. Fallback: fetch fail → placeholder image + brand `"—"`. KHÔNG migrate DB, KHÔNG sửa BE DTO.
- **D-02** — `AdminOrder` TypeScript interface: thay inline interface bằng import `Order` từ `@/types`. Xóa `as any` cast (line 58). `getAdminOrderById` đã là `Promise<Order>` rồi.
- **D-03** — Admin items layout: reuse cấu trúc table 4 cột giống user (Sản phẩm / Số lượng / Đơn giá / Thành tiền) + thumbnail bên trái. Inline-style (giữ pattern admin), KHÔNG tạo CSS module mới.
- **D-04** — Admin shipping/payment cards: render `order.shippingAddress` (street, ward, district, city join `, `) + `order.paymentMethod` (qua `paymentMethodMap`). Extract `paymentMethodMap` sang `@/lib/orderLabels.ts`.
- **D-05** — Empty items: render `<p>Đơn hàng không có sản phẩm</p>`.
- **D-06** — Image: `next/image` 64×64 với fallback `<div>` 64×64 background `var(--surface-container-high)` + icon placeholder.

### Claude's Discretion
- Naming class CSS / inline style keys
- Order của items trong table (theo `id` BE return — KHÔNG re-sort)
- Spacing/gap chính xác giữa thumbnail và text trong table cell
- Có hiển thị `productId` cho admin debugging hay không (lean: KHÔNG)

### Deferred Ideas (OUT OF SCOPE)
- Batch product fetch endpoint `GET /api/products?ids=...` — defer v1.4+
- Snapshot product image/brand vào `OrderItemEntity` (Flyway V6) — defer
- Click thumbnail → product detail navigation — defer
- Admin "AI suggest reply" button — thuộc AI-05 Phase 22
- Coupon display row — thuộc COUP-05 Phase 20
- Review CTA bên cạnh từng item — defer
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ORDER-01 | User order detail `/profile/orders/[id]` hiển thị full line items (image / name / brand / price / qty / subtotal). Verify BE `findByIdWithItems()` đã return đúng. | BE verified: `OrderCrudService.getOrder()` line 117-122 dùng `findByIdWithItems()` + `@Transactional(readOnly=true)` → eager fetch-join. Enrichment image+brand qua `getProductById` parallel. FE table 4-cột đã có (lines 152-177), chỉ cần extend cell "Sản phẩm" thêm `<Image>` + brand subtitle. |
| ADMIN-06 | Admin order detail `/admin/orders/[id]` hiển thị full line items + sửa `AdminOrder` interface có `items[]`. | FE-only fix: xóa placeholder line 145, thay `interface AdminOrder` (lines 13-22) bằng import `Order` từ `@/types`, render shipping/payment thật từ `order.shippingAddress` + `order.paymentMethod`, render items table identical với user side. |
</phase_requirements>

## Project Constraints (from CLAUDE.md / memory)

- **Vietnamese language**: chat / docs / commits dùng tiếng Việt; identifiers + commit prefix giữ EN.
- **Visible-first priority**: defer backend hardening invisible. Phase 17 thuần FE, KHÔNG sửa BE.
- **Project nature**: dự án thử nghiệm GSD workflow — KHÔNG phải PTIT/HTPT student assignment.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Fetch order detail (with items) | API / Backend (order-svc) | — | Endpoint `GET /api/orders/{id}` + `/api/orders/admin/{id}` đã trả `OrderDto` với items eager-fetched. |
| Enrich item với image+brand | Frontend (Next.js client component) | API (product-svc qua gateway) | D-01 lock: KHÔNG mutate BE DTO; FE gọi `getProductById` parallel sau khi load order. |
| Render items table + thumbnails | Browser / Client (React) | — | Pure UI rendering, `next/image` cho optimization. |
| Status / payment label translation | Frontend (lib helper) | — | Vietnamese labels — UI concern, extract sang `@/lib/orderLabels.ts`. |
| Auth / role guard cho admin route | Frontend Middleware | API (JWT validate) | Existing middleware (Phase 5) — KHÔNG đụng. |

## Standard Stack

### Core (đã có trong project — KHÔNG cần install thêm)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `next` | 16.2.3 | App Router framework | Đã dùng project-wide [VERIFIED: package.json] |
| `react` | 19.2.4 | UI library | Đã dùng [VERIFIED: package.json] |
| `next/image` | (built-in) | Optimized image rendering | D-06 lock; precedent Phase 15 PUB-01 [VERIFIED: 7 files dùng next/image trong codebase] |

### Supporting (zero new dependencies)

| Asset | Source | Purpose |
|-------|--------|---------|
| `getProductById` | services/products.ts:55 | Enrich image+brand cho từng item |
| `getOrderById` / `getAdminOrderById` | services/orders.ts:62, 77 | Đã `Promise<Order>` — KHÔNG đổi signature |
| `httpGet` (auto-unwrap envelope) | services/http.ts:108 | KHÔNG cần handle `.data` thủ công |
| `Order`, `OrderItem`, `Product` types | types/index.ts:188-218 | `Order.items?: OrderItem[]` + `Order.shippingAddress?: Address` + `Product.thumbnailUrl/brand` đã đủ shape |
| `paymentMethodMap`, `statusMap` | profile/orders/[id]/page.tsx:13-32 | DRY: extract sang `@/lib/orderLabels.ts` |
| `<Image>` từ next/image | Phase 15 precedent | next.config.ts:14-25 đã whitelist `images.unsplash.com` + `lh3.googleusercontent.com` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Parallel `getProductById` (D-01) | Batch endpoint `GET /api/products?ids=...` | Cần thêm BE endpoint — defer v1.4+ per CONTEXT.md |
| Snapshot image+brand vào OrderItemEntity | Flyway V6 migration | Thay đổi data model invisible — defer per visible-first |

## Architecture Patterns

### System Architecture Diagram

```
┌──────────────────────────────────────────────────────────┐
│  User browser                                            │
│  ┌────────────────────┐    ┌──────────────────────────┐  │
│  │ /profile/orders/   │    │ /admin/orders/[id]       │  │
│  │   [id]/page.tsx    │    │   page.tsx               │  │
│  └─────────┬──────────┘    └────────┬─────────────────┘  │
│            │                        │                    │
│            ▼                        ▼                    │
│   ┌─────────────────────────────────────────┐            │
│   │ useEnrichedItems(order.items)           │            │
│   │  └→ Promise.allSettled(                 │            │
│   │       uniqueProductIds.map(getById)     │            │
│   │     )                                   │            │
│   │  └→ merge thumbnailUrl + brand → VM     │            │
│   └────┬────────────────────────────────────┘            │
└────────┼─────────────────────────────────────────────────┘
         │
         ▼ HTTP via api-gateway:8080
┌─────────────────────────────────────────────────────────┐
│  API Gateway (Spring Cloud Gateway)                     │
│   /api/orders/{id}      → order-svc /orders/{id}        │
│   /api/orders/admin/{id}→ order-svc /admin/orders/{id}  │
│   /api/products/{id}    → product-svc /products/{id}    │
└──────────┬──────────────────────────────┬───────────────┘
           ▼                              ▼
   ┌──────────────────┐         ┌──────────────────────┐
   │ order-service    │         │ product-service      │
   │ getOrder() uses  │         │ returns Product with │
   │ findByIdWith     │         │ thumbnailUrl + brand │
   │ Items() — eager  │         └──────────────────────┘
   │ JOIN FETCH       │
   └──────────────────┘
```

### Recommended Project Structure (delta only)

```
sources/frontend/src/
├── app/
│   ├── admin/orders/[id]/page.tsx       # MODIFY
│   └── profile/orders/[id]/
│       ├── page.tsx                     # MODIFY (extend items table)
│       └── page.module.css              # MODIFY (thêm thumbnail column style)
└── lib/
    └── orderLabels.ts                   # NEW (extract paymentMethodMap, statusMap)
```

### Pattern 1: FE-side item enrichment (D-01)

**What:** Sau khi load order, fire parallel `getProductById` cho mỗi unique productId, merge thumbnailUrl+brand vào view-model.
**When to use:** Phase 17 cho cả admin + user page.

```tsx
// src/lib/useEnrichedItems.ts (suggested)
import { useEffect, useState } from 'react';
import { getProductById } from '@/services/products';
import type { OrderItem } from '@/types';

export type EnrichedItem = OrderItem & { thumbnailUrl?: string; brand?: string };

export function useEnrichedItems(items: OrderItem[] | undefined) {
  const [map, setMap] = useState<Record<string, { thumbnailUrl?: string; brand?: string }>>({});

  useEffect(() => {
    if (!items?.length) return;
    const uniqueIds = [...new Set(items.map(i => i.productId))];
    let cancelled = false;
    Promise.allSettled(uniqueIds.map(id => getProductById(id))).then(results => {
      if (cancelled) return;
      const next: typeof map = {};
      results.forEach((r, i) => {
        if (r.status === 'fulfilled' && r.value) {
          next[uniqueIds[i]] = { thumbnailUrl: r.value.thumbnailUrl, brand: r.value.brand };
        }
      });
      setMap(next);
    });
    return () => { cancelled = true; };
  }, [items]);

  return (items ?? []).map(it => ({ ...it, ...map[it.productId] }) as EnrichedItem);
}
```

### Pattern 2: Replace inline interface với shared `Order` type (D-02)

```tsx
// admin/orders/[id]/page.tsx (BEFORE)
interface AdminOrder { id: string; userId: string; total?: number; ... }
const data = await getAdminOrderById(id) as any;  // ❌

// AFTER
import type { Order } from '@/types';
const [order, setOrder] = useState<Order | null>(null);
const data = await getAdminOrderById(id);  // ✅ already Promise<Order>
setOrder(data);
```

### Pattern 3: next/image với placeholder fallback (D-06)

```tsx
import Image from 'next/image';

{item.thumbnailUrl ? (
  <Image src={item.thumbnailUrl} width={64} height={64} alt={item.productName}
         style={{ borderRadius: 'var(--radius-md)', objectFit: 'cover' }} />
) : (
  <div style={{ width: 64, height: 64, borderRadius: 'var(--radius-md)',
                background: 'var(--surface-container-high)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: 'var(--on-surface-variant)' }}>
    📦
  </div>
)}
```

### Anti-Patterns to Avoid

- **Mutate `OrderItem` type với image/brand fields** — pollute domain type cho UI concern. Dùng view-model `EnrichedItem` thay vì.
- **Fetch product trong loop sequential** — dùng `Promise.allSettled` parallel.
- **Tạo CSS module mới cho admin** — D-03 khóa: admin page 100% inline-style, giữ pattern.
- **Tạo `AdminOrder` interface mới** — D-02 khóa: dùng `Order` từ `@/types`.
- **Block render nếu enrichment fail** — D-01 fallback: render placeholder image + brand `"—"`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Image lazy-load + responsive | Custom `<img>` + IntersectionObserver | `next/image` | Built-in optimization, đã whitelist domains |
| Envelope unwrap | Manual `.then(r => r.data)` | `httpGet` | services/http.ts:108 đã auto-unwrap |
| Parallel fetch + partial-fail | `Promise.all` (fails-fast) | `Promise.allSettled` | Cho phép 1 product fetch fail mà không kill toàn bộ render |
| Order type definition | Tạo lại interface cho admin | Import `Order` từ `@/types` | Type đã đầy đủ shape (line 188-218 types/index.ts) |

**Key insight:** Phase 17 = 95% reuse hiện có, 5% glue code. Tránh mọi cám dỗ thêm abstraction.

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `sources/frontend/src/app/admin/orders/[id]/page.tsx` | MODIFY | Xóa `interface AdminOrder` (lines 13-22). Xóa `as any` cast (line 58). Thay state `useState<AdminOrder>` → `useState<Order>`. Xóa placeholder line 145, render items table 4-cột + thumbnail. Render `order.shippingAddress` (join `, `) + `order.paymentMethod` qua `paymentMethodMap`. Empty state per D-05. |
| `sources/frontend/src/app/profile/orders/[id]/page.tsx` | MODIFY | Extend cell "Sản phẩm" của table (line 167) thêm `<Image>` 64×64 + brand subtitle bên dưới `productName`. Empty state per D-05. Import `paymentMethodMap`/`statusMap` từ `@/lib/orderLabels.ts` thay vì define inline. |
| `sources/frontend/src/app/profile/orders/[id]/page.module.css` | MODIFY | Thêm class cho thumbnail cell (flex container 64×64 + gap text). Có thể tận dụng `.itemImg` (line 30) đã có sẵn — confirm khi implement. |
| `sources/frontend/src/lib/orderLabels.ts` | NEW | Export `paymentMethodMap`, `paymentStatusMap`, `statusMap` (từ profile/orders/[id]/page.tsx:13-32). Constant maps Vietnamese labels. |
| `sources/frontend/src/lib/useEnrichedItems.ts` | NEW (suggested) | Custom hook parallel `getProductById` enrichment (Pattern 1). Optional — có thể inline trong cả 2 page nếu planner thấy hook overkill cho 2 callers. |
| `sources/frontend/e2e/admin-orders.spec.ts` | EXTEND (optional) | Thêm assertion: placeholder text "Chi tiết sản phẩm sẽ khả dụng" KHÔNG xuất hiện ở `/admin/orders/[id]`. |
| `sources/frontend/e2e/order-detail.spec.ts` | EXTEND (optional) | Thêm assertion: items table có ≥1 row + thumbnail `<img>` xuất hiện. |

## Backend Verification Findings

**Verdict: Backend OK — KHÔNG cần modification.**

| Check | Finding | Source |
|-------|---------|--------|
| `OrderDto` có `items` field? | ✅ Yes — `List<OrderItemDto> items` (line 23) | OrderDto.java:17-28 |
| `OrderDto` có `shippingAddress`? | ✅ Yes — `Map<String, Object>` JSONB (line 24) | OrderDto.java:24 |
| `OrderDto` có `paymentMethod`? | ✅ Yes — `String paymentMethod` (line 25) | OrderDto.java:25 |
| `OrderItemDto` có image/brand? | ❌ No — chỉ `id, productId, productName, quantity, unitPrice, lineTotal` | OrderItemDto.java:5-12 — đây là root cause cần FE enrichment (D-01) |
| Admin endpoint trả items đầy đủ? | ✅ Yes — `getOrder(id, true)` dùng `findByIdWithItems()` eager fetch-join | AdminOrderController.java:42-44 + OrderCrudService.java:117-122 |
| User endpoint trả items đầy đủ? | ✅ Yes — `findByIdWithItems()` cùng path code | OrderCrudService.java:118 |
| LazyInitialization risk? | ❌ Mitigated — `@Transactional(readOnly=true)` + JOIN FETCH | OrderCrudService.java:117 (bug fix orders-api-500 đã giải quyết) |
| N+1 trên BE? | ❌ No — single JOIN FETCH query | findByIdWithItems repository method |
| Envelope wrap | ✅ `ApiResponse<OrderDto>` qua ApiResponseAdvice — FE auto-unwrap qua http.ts:108 | AdminOrderController.java:43 |

**Implication:** FE chỉ cần parse response (đã wrap đúng), reshape interface, render UI. KHÔNG cần Flyway migration, KHÔNG cần BE controller change.

## FE Enrichment Pattern Recommendation

**Pattern:** `useEnrichedItems(items)` custom hook (xem Pattern 1 ở trên).

**Rationale:**
- Codebase chưa có pattern parallel product-fetch sẵn (`Promise.allSettled` chỉ xuất hiện ở `admin/page.tsx` cho dashboard stats — khác use case).
- Hook isolation cho phép test riêng + reuse cho admin + user.
- `useMemo` theo `order.id` (D-01) — implement bằng `useEffect` deps `[items]` (items reference stable sau khi setOrder).
- **Cleanup:** dùng `cancelled` flag trong useEffect để tránh setState sau unmount (race nếu user navigate đi giữa lúc fetch).
- **Dedup:** `[...new Set(items.map(i => i.productId))]` — order legacy có thể có duplicate productId (cùng SP mua 2 lần) → tránh fetch trùng.

**Alternative (nếu planner muốn lean hơn):** Inline trong từng page (chấp nhận duplicate ~15 dòng code). Trade-off: ít abstraction nhưng harder to test. Recommend **hook approach** vì DRY + testable.

**Cache scope:** In-component state (`useState`). KHÔNG cần global cache (React Query / SWR) — phase visible-first defer caching infrastructure.

## Risks & Pitfalls

### Pitfall 1: Product bị soft-delete → `getProductById` trả 404 / null
**What goes wrong:** Order legacy reference productId của SP đã bị admin xóa → fetch fail → render thiếu image+brand.
**Why it happens:** Product service không cascade delete (đúng — order phải giữ history).
**How to avoid:** `Promise.allSettled` (KHÔNG `Promise.all`), `result.status === 'fulfilled'` mới merge. Fallback render placeholder + brand `"—"` (D-01).
**Warning signs:** Console log 404 cho `/api/products/{deletedId}`.

### Pitfall 2: shippingAddress shape có thể null cho legacy orders
**What goes wrong:** Order trước Phase 8 (PERSIST-02) có thể có `shippingAddress = null` trong DB.
**Why it happens:** Migration không backfill old rows.
**How to avoid:** Optional chaining: `order.shippingAddress?` + nếu null render `"—"`. Type `Order.shippingAddress?: Address` đã optional.
**Warning signs:** Admin/user xem order cũ thấy address card `"—"`.

### Pitfall 3: `Order.items` rỗng/null cho legacy orders
**What goes wrong:** Orders trước Phase 8 có `items = []` (chưa có OrderItemEntity rows).
**How to avoid:** D-05 lock — render `<p>Đơn hàng không có sản phẩm</p>` thay vì table empty body.
**Warning signs:** Table header xuất hiện không có row.

### Pitfall 4: N+1 nếu order có rất nhiều items
**What goes wrong:** Order 50 items → 50 parallel HTTP calls. Browser concurrency limit (~6) sẽ stagger.
**Why it happens:** Per-item fetch instead of batch.
**How to avoid:** Acceptable cho MVP (real orders 1-5 items). Defer batch endpoint v1.4+ per CONTEXT.md.
**Warning signs:** Network tab thấy >10 product calls cho 1 order detail load.

### Pitfall 5: Brand có thể null trong `Product` DTO
**What goes wrong:** Product mới seed có thể chưa có brand.
**How to avoid:** Render `item.brand ?? '—'`. Type `Product.brand?: string` đã optional.

### Pitfall 6: next/image domain whitelist
**What goes wrong:** `<Image src="...">` từ domain chưa whitelist trong `next.config.ts` → runtime error.
**How to avoid:** Đã whitelist `images.unsplash.com` + `lh3.googleusercontent.com` (next.config.ts:14-25). Phase 16 seed dùng Unsplash → đã cover. Nếu seed legacy dùng domain khác, fallback placeholder render.
**Warning signs:** Console error `Invalid src prop ... not configured under images in your next.config.js`.

### Pitfall 7: `cancelled` flag missing → setState after unmount warning
**What goes wrong:** User navigate đi trong khi `Promise.allSettled` chưa resolve → React warning "Can't perform state update on unmounted component".
**How to avoid:** Pattern `let cancelled = false; ... return () => { cancelled = true; }` trong useEffect.

### Pitfall 8: `useEffect` deps `[items]` re-run nếu `setOrder` tạo reference mới mỗi render
**What goes wrong:** Nếu items reference thay đổi mỗi render → infinite fetch loop.
**How to avoid:** Items chỉ thay đổi sau `setOrder(data)` (1 lần load). Verify deps stable. Optionally dùng `[order?.id]` deps thay vì `[items]`.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Playwright `^1.59.1` (E2E only — KHÔNG có unit/component test framework như Jest/Vitest) |
| Config file | `sources/frontend/playwright.config.ts` |
| Quick run command | `cd sources/frontend && npx playwright test e2e/order-detail.spec.ts e2e/admin-orders.spec.ts` |
| Full suite command | `cd sources/frontend && npx playwright test` |

**Note:** Project hiện KHÔNG có unit/component test runner (no Jest, no Vitest in package.json). Validation cho Phase 17 dùng:
- **TypeScript compiler** (`npx tsc --noEmit`) — verify type fixes (xóa `as any`).
- **ESLint** (`npm run lint`) — verify code quality.
- **Playwright E2E** — verify UI behavior end-to-end.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| ORDER-01 | `/profile/orders/[id]` render items table với image + name + brand + qty + price + subtotal | E2E (Playwright) | `npx playwright test e2e/order-detail.spec.ts -g "ORD-DTL-2"` (extend assertion thêm `<img>` + brand text) | ✅ extend existing |
| ORDER-01 | Empty items state render "Đơn hàng không có sản phẩm" | E2E manual | KHÔNG có legacy order rỗng trong test data — manual UAT | ❌ manual-only |
| ADMIN-06 | `/admin/orders/[id]` KHÔNG còn placeholder "Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8" | E2E (Playwright) | `npx playwright test e2e/admin-orders.spec.ts` (thêm assertion `expect(page).not.toContainText('khả dụng sau khi Phase 8')`) | ✅ extend existing |
| ADMIN-06 | `/admin/orders/[id]` render shipping address + payment method | E2E (Playwright) | Same file — assertion h3 "Thông tin giao hàng" + text Vietnamese label | ✅ extend existing |
| ADMIN-06 | `AdminOrder` interface dùng `items: OrderItem[]` (TypeScript) | Static (tsc) | `npx tsc --noEmit` từ `sources/frontend/` | ✅ tsc đã có |
| Cả hai | Enrichment fail (product 404) → render placeholder image + brand "—" | E2E hard to mock | Manual UAT — soft-delete 1 product trong DB rồi xem order chứa product đó | ❌ manual-only |

### Sampling Rate

- **Per task commit:** `npx tsc --noEmit && npm run lint` (~10s)
- **Per wave merge:** `npx playwright test e2e/order-detail.spec.ts e2e/admin-orders.spec.ts` (~30-60s)
- **Phase gate:** Full Playwright suite green + manual UAT cho 2 case empty/soft-deleted product.

### Wave 0 Gaps

- [ ] Extend `e2e/admin-orders.spec.ts` — thêm test "ADMIN-ORD-DETAIL: render items + KHÔNG còn placeholder" (cần seed order có items qua `global-setup.ts` hoặc dùng order existing trong test DB).
- [ ] Extend `e2e/order-detail.spec.ts` ORD-DTL-2 — thêm assertion thumbnail `<img>` xuất hiện (data-testid hoặc role=img trong items table).
- [ ] Document trong SUMMARY.md các manual UAT cases (empty items + soft-deleted product) cần human verify.

*(KHÔNG cần thêm test framework mới — visible-first defer Jest/Vitest install. Playwright + tsc đủ cho phase này.)*

## Runtime State Inventory

> Phase 17 KHÔNG phải rename/refactor/migration — chỉ là bug fix UI + add FE enrichment. Section này N/A nhưng include explicit answer:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — không rename DB column / collection / key | None |
| Live service config | None — không đụng n8n / external service | None |
| OS-registered state | None — không đụng OS task / scheduler | None |
| Secrets/env vars | None — không thêm secret mới | None |
| Build artifacts | None — npm install không chạy (zero new deps) | None |

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | Next.js dev/build | ✓ (assumed — frontend đang dev được) | ≥18 | — |
| npm | package management | ✓ | — | — |
| Playwright browsers | E2E test | ✓ (đã chạy trong v1.2 Phase 9) | 1.59.1 | — |
| api-gateway:8080 | Runtime (BE calls) | ✓ docker-compose | — | — |
| product-service | Runtime enrichment fetch | ✓ docker-compose | — | Fallback placeholder per D-01 |
| order-service | Runtime order fetch | ✓ docker-compose | — | RetrySection (đã có) |

**Missing dependencies:** None blocking. Phase thuần FE + reuse existing infrastructure.

## Code Examples

### Admin page after fix (skeleton)

```tsx
// admin/orders/[id]/page.tsx (key changes)
'use client';
import Image from 'next/image';
import type { Order } from '@/types';
import { paymentMethodMap, statusMap } from '@/lib/orderLabels';
import { useEnrichedItems } from '@/lib/useEnrichedItems';

const [order, setOrder] = useState<Order | null>(null);
// ...
const data = await getAdminOrderById(id);  // no `as any`
setOrder(data);
// ...
const enriched = useEnrichedItems(order?.items);

// Shipping card (replace lines 134-139)
<div style={cardStyle}>
  <h3 ...>Thông tin giao hàng</h3>
  <p style={labelStyle}>
    Địa chỉ: <strong>{
      order.shippingAddress
        ? [order.shippingAddress.street, order.shippingAddress.ward,
           order.shippingAddress.district, order.shippingAddress.city]
            .filter(Boolean).join(', ')
        : '—'
    }</strong>
  </p>
  <p style={labelStyle}>
    Thanh toán: <strong>{paymentMethodMap[order.paymentMethod] ?? order.paymentMethod ?? '—'}</strong>
  </p>
</div>

// Items card (replace lines 142-152)
<div style={cardStyle}>
  <h3 ...>Sản phẩm</h3>
  {enriched.length === 0 ? (
    <p>Đơn hàng không có sản phẩm</p>
  ) : (
    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
      <thead>...</thead>
      <tbody>
        {enriched.map(it => (
          <tr key={it.id}>
            <td style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', padding: 'var(--space-2)' }}>
              {/* thumbnail + name + brand */}
              {it.thumbnailUrl
                ? <Image src={it.thumbnailUrl} width={64} height={64} alt={it.productName} />
                : <div style={{ width: 64, height: 64, background: 'var(--surface-container-high)' }}>📦</div>}
              <div>
                <div>{it.productName}</div>
                <div style={{ fontSize: 'var(--text-body-sm)', color: 'var(--on-surface-variant)' }}>
                  {it.brand ?? '—'}
                </div>
              </div>
            </td>
            <td>{it.quantity}</td>
            <td>{(it.unitPrice ?? it.price)?.toLocaleString('vi-VN')}₫</td>
            <td>{(it.lineTotal ?? it.subtotal)?.toLocaleString('vi-VN')}₫</td>
          </tr>
        ))}
      </tbody>
    </table>
  )}
  {/* ... total row ... */}
</div>
```

### `lib/orderLabels.ts` (NEW)

```ts
export const paymentMethodMap: Record<string, string> = {
  COD: 'Thanh toán khi nhận hàng',
  BANK_TRANSFER: 'Chuyển khoản ngân hàng',
  E_WALLET: 'Ví điện tử',
};

export const paymentStatusMap: Record<string, string> = {
  PENDING: 'Chờ thanh toán',
  PAID: 'Đã thanh toán',
  FAILED: 'Thanh toán thất bại',
  REFUNDED: 'Đã hoàn tiền',
};

export const statusMap: Record<string, { label: string; variant: 'default' | 'new' | 'hot' | 'sale' | 'out-of-stock' }> = {
  PENDING:   { label: 'Chờ xác nhận', variant: 'default' },
  CONFIRMED: { label: 'Đã xác nhận',  variant: 'new' },
  SHIPPING:  { label: 'Đang giao',    variant: 'hot' },
  DELIVERED: { label: 'Đã giao',      variant: 'sale' },
  CANCELLED: { label: 'Đã hủy',       variant: 'out-of-stock' },
};
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Hardcoded placeholder text trong UI | Render real items từ BE response | Phase 17 (now) | UX visible improvement |
| Inline ad-hoc interface mismatched với BE DTO | Shared `Order` type từ `@/types` | Phase 17 | Type safety, xóa `as any` |
| Lazy items + open-in-view = LazyInitException | `findByIdWithItems()` JOIN FETCH | Phase 8 (already done) | BE đã safe |

**Deprecated/outdated:** None — phase chỉ thêm UI rendering trên BE đã sẵn sàng.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `Product.brand` populated cho most products sau Phase 16 seed | Pitfalls #5 | Render `'—'` cho mọi item — UX lờ mờ nhưng không break |
| A2 | E2E test data có ≥1 user order với items để assert | Validation Architecture | Test skip với note (đã có pattern `test.skip` trong order-detail.spec.ts:51) |
| A3 | Existing `useEffect` deps `[items]` không gây infinite loop (items stable sau setOrder) | Pitfalls #8 | Fallback dùng `[order?.id]` deps |
| A4 | `next/image` domain whitelist cover hết URL từ DB | Pitfalls #6 | Fallback placeholder render |

**If user prefers:** Có thể chuyển A2 sang manual UAT thay vì rely E2E.

## Open Questions

1. **`useEnrichedItems` hook vs inline duplicate code**
   - What we know: 2 callers (admin + user page) cần cùng pattern.
   - What's unclear: Planner có muốn extract hook (test riêng) hay accept duplicate (~15 dòng) cho lean.
   - Recommendation: Extract hook — test seam + DRY thắng 15 dòng overhead.

2. **Có cần thêm Playwright assertion cho enrichment thumbnail render?**
   - What we know: E2E hiện không assert image src cụ thể.
   - What's unclear: Test có wait đủ lâu cho `Promise.allSettled` resolve không.
   - Recommendation: Assert table có ≥1 row trước, thumbnail là nice-to-have (rely manual UAT).

3. **Có cần Vitest/Jest cho `useEnrichedItems` unit test?**
   - What we know: Project zero unit test framework hiện tại.
   - What's unclear: Bootstrap Vitest scope creep cho 1 hook?
   - Recommendation: NO — visible-first defer test infrastructure. Hook đơn giản, code review + manual UAT đủ.

## Sources

### Primary (HIGH confidence)
- Codebase inspection trực tiếp: tất cả file paths được liệt kê trong `## Files to Modify` đã đọc full content.
- BE DTO: `OrderDto.java`, `OrderItemDto.java`, `AdminOrderController.java`, `OrderCrudService.java` confirmed shape.
- FE service layer: `services/orders.ts`, `services/products.ts`, `services/http.ts` confirmed signatures.
- Type definitions: `types/index.ts` confirmed `Order.items?: OrderItem[]` exists.
- Project config: `next.config.ts`, `package.json`, `playwright.config.ts`, `.planning/config.json`.

### Secondary (MEDIUM confidence)
- CONTEXT.md decisions D-01 → D-06 (user-locked).
- Phase 8 history references (orders-api-500 bug fix comment in OrderCrudService.java:96-101).

### Tertiary (LOW confidence)
- None — toàn bộ findings verified bằng tool inspection.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — đã verify package.json, next.config.ts, services layer
- Architecture: HIGH — BE inspection confirm eager fetch, FE service confirm envelope unwrap
- Pitfalls: HIGH — pitfalls dựa trên code reading, không speculation

**Research date:** 2026-05-02
**Valid until:** 2026-06-01 (30 ngày — phase nhỏ, infrastructure stable)

---

## RESEARCH COMPLETE

**Phase:** 17 — Sửa Order Detail Items
**Confidence:** HIGH

### Key Findings
1. Backend đã sẵn sàng 100% — `OrderDto.items` + `shippingAddress` + `paymentMethod` đã trả qua eager JOIN FETCH trong `OrderCrudService.getOrder()`. KHÔNG cần BE modification.
2. `httpGet` đã auto-unwrap envelope (services/http.ts:108) — FE chỉ cần xóa `as any` cast và dùng `Order` type từ `@/types`.
3. FE enrichment image+brand qua `Promise.allSettled([getProductById, ...])` — codebase chưa có pattern, recommend extract custom hook `useEnrichedItems`.
4. Zero new dependencies — `next/image` đã built-in, Unsplash đã whitelist trong next.config.ts.
5. Validation: Playwright E2E (extend 2 spec hiện có) + tsc + ESLint. Project KHÔNG có unit test framework — defer Vitest install.

### File Created
`.planning/phases/17-s-a-order-detail-items/17-RESEARCH.md`

### Confidence Assessment
| Area | Level | Reason |
|------|-------|--------|
| Standard Stack | HIGH | package.json + next.config.ts inspected |
| Architecture | HIGH | BE service code + FE service code inspected end-to-end |
| Pitfalls | HIGH | Pitfalls dựa trên code reading + Phase 8 bug history |

### Open Questions
1. Hook vs inline duplicate (recommend hook).
2. Thumbnail E2E assert (recommend manual UAT).
3. Vitest bootstrap (recommend NO).

### Ready for Planning
Research complete. Planner có thể tạo PLAN.md với 4-5 task wave: (1) lib/orderLabels + useEnrichedItems extract, (2) admin page rewrite, (3) user page extend, (4) E2E test extend, (5) UAT verify.
