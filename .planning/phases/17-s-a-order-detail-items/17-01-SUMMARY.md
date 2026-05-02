---
phase: 17-s-a-order-detail-items
plan: 01
subsystem: ui
tags: [frontend, nextjs, react-hook, order-detail, vietnamese-i18n]

requires:
  - phase: 08
    provides: OrderDto.items + shippingAddress + paymentMethod (BE đã sẵn)
provides:
  - "@/lib/orderLabels (3 Vietnamese label maps reusable)"
  - "@/lib/useEnrichedItems (hook parallel-enrich items với thumbnailUrl + brand)"
affects: [17-02 admin order detail rewrite, 17-03 user order detail extend]

tech-stack:
  added: []
  patterns:
    - "FE-side data enrichment qua Promise.allSettled hook"
    - "View-model type extension (OrderItem & {thumbnailUrl, brand}) thay vì mutate domain type"
    - "Custom hook trong src/lib/ — đầu tiên trong project (precedent cho future hooks)"

key-files:
  created:
    - sources/frontend/src/lib/orderLabels.ts
    - sources/frontend/src/lib/useEnrichedItems.ts
  modified: []

key-decisions:
  - "D-01 implement: Promise.allSettled (KHÔNG Promise.all) → 1 product 404 không kill render"
  - "D-04 implement: paymentMethodMap extract verbatim — labels giữ nguyên cho idempotent migration ở Wave 2"
  - "Hook pattern: useState + useEffect + cancelled flag + Set dedup (per RESEARCH Pitfalls #5/#7/#8)"

patterns-established:
  - "useEnrichedItems(items): hook chuẩn cho FE-side enrichment qua Promise.allSettled + cleanup flag"
  - "@/lib/orderLabels: single source-of-truth cho Vietnamese order/payment labels — DRY giữa admin + user pages"

requirements-completed: [ORDER-01, ADMIN-06]

duration: 5min
completed: 2026-05-02
---

# Phase 17 Plan 01: Foundation `lib/orderLabels` + `useEnrichedItems` Hook Summary

**Vietnamese label maps + custom React hook parallel-enrich order items với thumbnailUrl/brand qua Promise.allSettled — foundation cho Wave 2 admin/user order detail page rewrite.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-05-02T14:27:50Z
- **Completed:** 2026-05-02T14:32:51Z
- **Tasks:** 2
- **Files modified:** 0 (2 created)

## Accomplishments

- `src/lib/orderLabels.ts` — 3 Vietnamese label maps (`statusMap`, `paymentMethodMap`, `paymentStatusMap`) extract verbatim từ `profile/orders/[id]/page.tsx:13-32`.
- `src/lib/useEnrichedItems.ts` — Custom hook parallel-fetch `getProductById` cho mỗi unique productId, merge `thumbnailUrl + brand` vào `EnrichedItem` view-model. Dùng `Promise.allSettled` + `cancelled` cleanup + Set dedup.
- Wave 2 plans (17-02 admin + 17-03 user) có thể `import` ngay không cần modify thêm.

## Task Commits

1. **Task 1: Tạo `src/lib/orderLabels.ts` với 3 Vietnamese label maps** — `3018818` (feat)
2. **Task 2: Tạo `src/lib/useEnrichedItems.ts` custom hook (D-01 enrichment)** — `8adc384` (feat)

## Files Created/Modified

- `sources/frontend/src/lib/orderLabels.ts` (NEW, 27 lines) — 3 export const maps Vietnamese labels.
- `sources/frontend/src/lib/useEnrichedItems.ts` (NEW, 42 lines) — Hook + EnrichedItem type export.

## Hook Signature

```ts
export type EnrichedItem = OrderItem & {
  thumbnailUrl?: string;
  brand?: string;
};

export function useEnrichedItems(items: OrderItem[] | undefined): EnrichedItem[];
```

Wave 2 import paths:
- `import { statusMap, paymentMethodMap, paymentStatusMap } from '@/lib/orderLabels';`
- `import { useEnrichedItems, type EnrichedItem } from '@/lib/useEnrichedItems';`

## Verification Status

- `npx tsc --noEmit` → **PASS** (exit 0, no type errors).
- `npm run lint` → **PASS cho 2 file mới** (0 errors trong `orderLabels.ts` + `useEnrichedItems.ts`). Pre-existing lint issues trong files khác (admin/page.tsx, AddressPicker.tsx, admin/orders/* pages, e2e specs) ghi nhận trong `deferred-items.md` — out of scope per Scope Boundary rule.
- Acceptance grep checks: 3/3 cho orderLabels (`^export const ` count=3, `paymentMethodMap` match, `Chờ xác nhận` match) + 6/6 cho useEnrichedItems (`'use client'`, `Promise.allSettled`, `let cancelled`, `export type EnrichedItem`, `export function useEnrichedItems`, `new Set(items.map`).

## Decisions Made

- **`useState + useEffect + cancelled flag`** thay vì sophisticated state lib (TanStack Query / SWR) — visible-first defer caching infrastructure, hook đủ đơn giản.
- **Deps `[items]`** thay vì `[order?.id]` — items reference stable sau `setOrder()` 1 lần (verified safe per RESEARCH Pitfall #8).
- **NO unit test framework bootstrap** — project zero Vitest/Jest, defer per visible-first. tsc + lint + downstream E2E qua Wave 2 đủ confidence.

## Deviations from Plan

None — plan executed exactly as written.

Pre-existing lint warnings/errors trong files khác (admin/page.tsx:59, AddressPicker.tsx:39, e2e specs, admin/orders/* — sẽ rewrite ở Plan 17-02) được log riêng trong `deferred-items.md` per Scope Boundary rule (chỉ auto-fix issues trực tiếp do task gây ra).

## Issues Encountered

- `node_modules` chưa cài lúc bắt đầu (worktree fresh) → chạy `npm install` (~17s, 368 packages) trước khi verify tsc/lint. Không phải bug, expected setup.

## Self-Check: PASSED

**Files:**
- FOUND: sources/frontend/src/lib/orderLabels.ts
- FOUND: sources/frontend/src/lib/useEnrichedItems.ts

**Commits:**
- FOUND: 3018818 (Task 1)
- FOUND: 8adc384 (Task 2)

## Next Phase Readiness

**Wave 2 ready:** Plans 17-02 (admin) và 17-03 (user) có thể import `@/lib/orderLabels` + `@/lib/useEnrichedItems` ngay. Foundation đầy đủ — KHÔNG cần modify lib files thêm.

**Blockers:** None.

---
*Phase: 17-s-a-order-detail-items*
*Completed: 2026-05-02*
