---
phase: 18-storage-audit-cart-db
plan: "04"
subsystem: ui
tags: [react-query, cart, frontend, hooks, mutations]

requires:
  - phase: 18-03
    provides: useCart + 4 mutation hooks + parseCartError trong hooks/useCart.ts

provides:
  - cart/page.tsx subscribe useCart(), mutations qua useUpdateCartItem/useRemoveCartItem
  - checkout/page.tsx fetch cart async qua useCart, clear sau order qua useClearCart
  - Header.tsx cart badge live từ React Query cache (cartCount = sum quantities)

affects:
  - phase-18-05 (mergeGuestCart wire sẽ invalidate ['cart'] → Header badge auto-refresh)
  - checkout flow toàn bộ (cart state không còn từ localStorage sync)

tech-stack:
  added: []
  patterns:
    - "React Query consumer pattern: useCart() + useMutation + onError toast vi"
    - "Non-blocking clear after order: try/catch clearMutation.mutateAsync() trước router.push"
    - "Mutation pending guard: disabled={mutation.isPending} chống double-click race"

key-files:
  created: []
  modified:
    - sources/frontend/src/app/cart/page.tsx
    - sources/frontend/src/app/checkout/page.tsx
    - sources/frontend/src/components/layout/Header/Header.tsx

key-decisions:
  - "checkout clearCart non-blocking: nếu clear fail vẫn router.push (T-18-16 accept)"
  - "Header badge = sum of quantities (không phải số dòng item)"
  - "Stock modal handlers dùng mutateAsync (await) để đảm bảo cache invalidate trước khi setStockModal(null)"

patterns-established:
  - "onError: parseCartError(err) → showToast(ctx.message, 'error') — chuẩn vi error toast cho tất cả cart mutations"
  - "hydrated = !isLoading (thay vì useState hydrated flag thủ công)"

requirements-completed:
  - STORE-02

duration: 15min
completed: "2026-05-02"
---

# Phase 18 Plan 04: Cart Consumer Refactor — useCart Hooks Summary

**3 FE consumers (cart page, checkout page, Header badge) refactored từ sync localStorage sang React Query hooks — user logged-in thấy DB cart, guest thấy localStorage, Header badge live-update sau mọi mutation**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-05-02T~15:30Z
- **Completed:** 2026-05-02T~15:45Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments

- cart/page.tsx: `useState(readCart()) + cart:change listener` → `useCart()` hook; +/- qty và remove qua mutations với toast lỗi tiếng Việt (STOCK_SHORTAGE)
- checkout/page.tsx: `readCart()` → `useCart()`; `clearCart()` sau order → `clearMutation.mutateAsync()` (non-blocking); stock modal handlers dùng `mutateAsync` thay direct service calls
- Header.tsx: badge hardcoded `0` → `cartCount` từ `useCart()` subscribe React Query cache — tự cập nhật khi bất kỳ mutation nào invalidate `['cart']` key

## Task Commits

1. **Task 1: Refactor cart/page.tsx sang useCart + mutation hooks** - `3444409` (feat)
2. **Task 2: Refactor checkout/page.tsx sang useCart + useClearCart** - `b0a8c8f` (feat)
3. **Task 3: Update Header.tsx cart badge subscribe useCart()** - `eb83e68` (feat)

## Files Created/Modified

- `sources/frontend/src/app/cart/page.tsx` — Dùng useCart, useUpdateCartItem, useRemoveCartItem; onError toast vi; disable buttons khi mutation pending
- `sources/frontend/src/app/checkout/page.tsx` — Dùng useCart, useClearCart; non-blocking clearMutation sau order; stock modal handlers với mutateAsync
- `sources/frontend/src/components/layout/Header/Header.tsx` — Thêm useCart import; cartCount = sum quantities; badge live từ React Query cache

## Decisions Made

- **checkout clearCart non-blocking (T-18-16 accept):** Nếu clear cart fail sau order OK → log error nhưng vẫn redirect (order đã tạo, không rollback). User có thể manual clear sau.
- **Header badge = sum quantities:** `cartItems.reduce((sum, i) => sum + i.quantity, 0)` — phản ánh tổng số lượng, không phải số loại SP.
- **Stock modal await mutateAsync:** Dùng `await Promise.all(...)` với `mutateAsync` để React Query invalidate cache trước `setStockModal(null)` — tránh stale data trong UI.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Header.tsx bị empty do disk full khi ghi**
- **Found during:** Task 3 (Header.tsx edit)
- **Issue:** Disk C: đầy 100% (213GB/213GB), lệnh ghi đầu tiên tạo file empty
- **Fix:** Xóa node_modules từ các workspace cũ (phase-15, phase-17) để giải phóng ~1.4GB, restore Header.tsx từ `git show HEAD:...`, sau đó edit thành công
- **Files modified:** Header.tsx (restored + edited)
- **Verification:** `npx tsc --noEmit` exits 0
- **Committed in:** eb83e68

---

**Total deviations:** 1 auto-fixed (Rule 3 - blocking environment issue)
**Impact on plan:** Không ảnh hưởng kết quả — file cuối cùng đúng như plan spec. Disk space issue là external blocker, không liên quan đến code.

## Issues Encountered

- **Disk C: 100% full** khi bắt đầu Task 3. Nguyên nhân: OneDrive cache ~296GB + nhiều workspace node_modules. Giải pháp: xóa node_modules của phase-15 và phase-17 workspaces (không cần thiết khi execute phase-18). Còn ~1.4GB sau khi clean.

## Known Stubs

Không có stub — tất cả data source đã wired thật:
- cart/page.tsx: data từ `useCart()` → `fetchCart()` → localStorage (guest) / API (user)
- checkout/page.tsx: cart data từ `useCart()`, clear qua `useClearCart()`
- Header.tsx: cartCount từ `useCart()` live React Query cache

## Threat Flags

Không có surface mới ngoài plan's threat model.

## Next Phase Readiness

- Plan 05 (mergeGuestCartToServer wire): sau login, `invalidateQueries(['cart'])` sẽ trigger Header badge refetch tự động — không cần thay đổi Header.tsx
- Manual smoke test cần Plan 05 để verify cross-device persist (user cart từ DB)
- Guest cart path hoạt động ngay: fetchCart() routing qua _localRead() cho guest

---
*Phase: 18-storage-audit-cart-db*
*Completed: 2026-05-02*
