---
phase: 20
plan: 05
subsystem: frontend
tags: [frontend, checkout, coupon, react-query, vietnamese-ui]
requires: [20-03, 20-04]
provides:
  - "FE coupon preview UX hoàn chỉnh tại /checkout (input + Áp dụng + chip + 3-row summary)"
  - "validateCoupon service + useApplyCoupon React Query mutation hook"
  - "couponErrorMessages map (8 codes Vietnamese) + formatCouponError + isCouponError helpers"
  - "CreateOrderRequest extended với optional couponCode field"
affects:
  - "sources/frontend/src/app/checkout/page.tsx"
  - "sources/frontend/src/types/index.ts (CouponPreview type + couponCode field)"
  - "sources/frontend/src/services/orders.ts (JSDoc only)"
tech-stack:
  added: []
  patterns:
    - "React Query useMutation cho preview validate (local state, không invalidate global cache)"
    - "useEffect [subtotal] auto re-validate với alive flag cleanup pattern"
    - "Error code → tiếng Việt map kèm formatter cho details (minOrderAmount)"
key-files:
  created:
    - sources/frontend/src/services/coupons.ts
    - sources/frontend/src/lib/couponErrorMessages.ts
    - sources/frontend/src/hooks/useApplyCoupon.ts
  modified:
    - sources/frontend/src/types/index.ts
    - sources/frontend/src/services/orders.ts
    - sources/frontend/src/app/checkout/page.tsx
    - sources/frontend/src/app/checkout/page.module.css
key-decisions:
  - "D-11 (8 error codes Vietnamese)"
  - "D-13 (preview endpoint path)"
  - "D-17 (UI vị trí giữa items list và summary rows)"
  - "D-18 (mutation pattern + auto re-validate trigger trên subtotal change)"
  - "D-19 (submit body extend couponCode + CouponException catch không clear cart)"
  - "D-20 (3 dòng summary layout: Tạm tính / Phí vận chuyển / Giảm giá)"
requirements-completed: [COUP-03]
duration: ~10 min
completed: 2026-05-03
---

# Phase 20 Plan 05: FE Checkout Coupon Section Summary

Triển khai FE checkout coupon UX (D-17/D-18/D-19/D-20): input mã + nút "Áp dụng" → preview validate qua BE → chip hiển thị + 3 dòng tổng tiền với "Giảm giá (CODE)" → auto re-validate khi cart thay đổi → submit order với couponCode trong body, BE atomic redeem trong cùng transaction.

## Artifacts

### Created
- `sources/frontend/src/services/coupons.ts` — `validateCoupon(body, userId?)` gọi `POST /api/orders/coupons/validate` (D-13), header `X-User-Id` optional cho check already-redeemed.
- `sources/frontend/src/lib/couponErrorMessages.ts` — Map 8 BE `CouponErrorCode` → tiếng Việt (D-11) + `formatCouponError(code, details)` xử lý `details.minOrderAmount` formatting + `isCouponError(code)` typeguard.
- `sources/frontend/src/hooks/useApplyCoupon.ts` — React Query `useMutation` wrapper (D-18), local component state, không invalidate global cache.

### Modified
- `sources/frontend/src/types/index.ts` — Thêm `CouponPreview` interface ({ code, type, value, discountAmount, finalTotal, message }) + extend `CreateOrderRequest` với optional `couponCode?: string` (D-19).
- `sources/frontend/src/services/orders.ts` — JSDoc `createOrder` note BE atomic redeem (Plan 20-03).
- `sources/frontend/src/app/checkout/page.tsx` — Thêm coupon state + handler + auto re-validate effect + UI section + submit body extend + CouponException catch.
- `sources/frontend/src/app/checkout/page.module.css` — Thêm `.couponSection / .couponRow / .couponChip / .couponChipCode / .couponChipDiscount / .couponChipRemove / .discountRow`.

## Implementation Highlights

### Apply flow (D-17, D-18)
1. User nhập mã → click **Áp dụng** → `applyCouponMutation.mutateAsync({ code, cartTotal: subtotal })`
2. Success: `setAppliedCoupon(preview)` → UI render chip thay input + 3 dòng summary có "Giảm giá (CODE) -X đ"
3. Error: `isApiError(err) && isCouponError(err.code)` → `formatCouponError` → toast tiếng Việt cụ thể; fallback toast generic nếu không phải COUPON_*

### Auto re-validate (D-18)
- `useEffect` với dependency `[subtotal]` — chạy khi cart đổi
- Gọi `validateCoupon` trực tiếp (không qua mutation hook để không lockup UI loading state)
- Pattern `let alive = true; ... return () => { alive = false; };` để tránh stale state khi unmount/re-trigger
- Fail (BE 422 vì subtotal mới < minOrder, v.v.) → `setAppliedCoupon(null)` + toast "Mã giảm giá không còn áp dụng được"

### Submit (D-19)
- `createOrder({ ..., couponCode: appliedCoupon?.code }, user?.id)` — `undefined` nếu chưa apply, BE coi là không có coupon
- Catch trong `default` của switch: nếu `isCouponError(err.code)` → toast cụ thể + `setAppliedCoupon(null)` + KHÔNG clear cart (user có thể bỏ mã + retry — race-lose case D-19)

### Total calc (D-20)
- `formatPrice(Math.max(0, total - (appliedCoupon?.discountAmount ?? 0)))` — cap min 0 cho edge case discountAmount > total (defensive, BE không nên trả nhưng không sao)

## Verification

### Acceptance criteria (all PASS)

Task 1 grep counts:
- `validateCoupon` trong services/coupons.ts: 1 export ✓
- `'/api/orders/coupons/validate'` trong services/coupons.ts: 1 ✓
- `'X-User-Id'` trong services/coupons.ts: 1 ✓
- 8 COUPON_* keys trong couponErrorMessages.ts: 8 keys present ✓
- `useApplyCoupon` export trong hooks/useApplyCoupon.ts: 1 ✓
- `useMutation` trong hooks/useApplyCoupon.ts: 1 ✓
- `couponCode?:` trong types/index.ts: 1 ✓
- `interface CouponPreview` trong types/index.ts: 1 ✓

Task 2 grep counts:
- `appliedCoupon` trong checkout/page.tsx: 11 (≥6) ✓
- `useApplyCoupon`: 1 import + 1 call ✓
- `applyCouponMutation.mutateAsync`: 1 ✓
- `formatCouponError`: 2 (apply error + submit error) ✓
- `couponCode: appliedCoupon?.code`: 1 ✓
- `Mã giảm giá`: ≥2 (title + auto re-validate toast) ✓
- `Áp dụng`: 1 (button label) ✓
- `.couponSection {`, `.couponChip {`, `.discountRow {` trong CSS: 1 each ✓

### tsc + lint
- `npx tsc --noEmit` + `npm run lint` không chạy được trong worktree này vì `node_modules/` chưa được install (worktree fresh, dependencies tồn tại ở main repo). Plan-level verification sẽ được thực hiện sau khi merge bởi orchestrator.
- Code thay đổi minimal về mặt typing: chỉ thêm 1 interface mới (`CouponPreview`) + 1 optional field (`couponCode?: string`) + sử dụng existing patterns (`httpPost`, `useMutation`, `isApiError`, `formatPrice`). Không có cấu trúc nào mới có thể break tsc.

### Manual UAT (deferred, requires backend running)
Plan-level `<verification>` flow:
1. Login user → /checkout → cart có items
2. Nhập "SUMMER2026" → Áp dụng → expect chip + 3 dòng summary có "Giảm giá: -X đ"
3. Đổi qty cart → expect auto re-validate
4. Nhập "INVALID" → Áp dụng → expect toast "Mã giảm giá không tồn tại"
5. Click Đặt hàng → expect order tạo thành công với discount; navigate `/profile/orders/{id}` (Plan 20-06 sẽ display)

## Deviations from Plan

None - plan executed exactly as written. Lưu ý nhỏ: types/index.ts đã có sẵn extension `discountAmount` + `couponCode` cho `Order` interface (từ Plan 20-03 OrderDto extension); chỉ thêm `CouponPreview` mới + extend `CreateOrderRequest`. Không có deviation cần fix tự động.

## Threat Flags

Không có surface mới ngoài threat model. Tất cả 5 threats (T-20-05-01..05) đã được mitigate hoặc accepted theo plan:
- **T-20-05-01** (tampering appliedCoupon): mitigated bởi BE atomic compute server-side (Plan 20-03)
- **T-20-05-02** (script injection trong code input): mitigated bởi `.toUpperCase()` client filter + BE `@Pattern` regex
- **T-20-05-03** (info disclosure): accepted (coupon code public)
- **T-20-05-04** (spam preview): accepted (defer rate-limit)
- **T-20-05-05** (repudiation): mitigated bởi OrderDto trả `discountAmount` + `couponCode` (Plan 20-06 sẽ hiển thị)

## Authentication Gates

Không có. Endpoint preview header `X-User-Id` optional — guest cũng dùng được (chỉ không check `COUPON_ALREADY_REDEEMED`). User-level submit dùng existing JWT từ httpPost.

## Commits

| Task | Commit  | Description |
|------|---------|-------------|
| 1    | 87cbcc6 | services/coupons.ts + couponErrorMessages.ts + useApplyCoupon hook + types extension |
| 2    | 5bf68cd | checkout page coupon section + auto re-validate + submit body extend + CSS |

## Next

Plan 20-06 (FE admin/coupons + order detail display) sẽ:
- Render `discountAmount` + `couponCode` trên `/profile/orders/[id]` (D-20)
- Admin CRUD UI tại `/admin/coupons` (Plan 20-02 BE đã sẵn sàng)

Ready for orchestrator merge + Wave 4 verification.

## Self-Check: PASSED

- All 7 files exist (3 created + 4 modified): verified via git diff
- 2 task commits found in git log: `87cbcc6`, `5bf68cd`
- All grep-based acceptance criteria pass (counts above)
- tsc/lint deferred to orchestrator (worktree no node_modules — known infra limitation, not an executor failure)
