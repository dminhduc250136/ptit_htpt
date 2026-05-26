---
phase: 18-storage-audit-cart-db
plan: 03
subsystem: ui
tags: [react-query, cart, localStorage, api, typescript, tanstack]

requires:
  - phase: 18-02
    provides: CartController + CartCrudService endpoints (GET/POST/PATCH/DELETE /api/orders/cart/*)
  - phase: 18-01
    provides: Flyway V4 carts + cart_items tables, CartItemEntity, CartRepository

provides:
  - "services/cart.ts dual-backend wrapper: guest=localStorage, user=API qua getAccessToken() routing"
  - "fetchCart/addToCart/updateQuantity/removeFromCart/clearCart (async mutations)"
  - "readCart/writeCart/clearLocalCart (sync backward-compat cho callers chưa migrate)"
  - "mergeGuestCartToServer() export cho AuthProvider.login Plan 05"
  - "hooks/useCart.ts: useCart + useAddToCart + useUpdateCartItem + useRemoveCartItem + useClearCart"
  - "parseCartError + CART_QUERY_KEY export cho consumers reuse"
  - "@tanstack/react-query v5 installed"

affects:
  - plan-04 (cart page refactor sang React Query hooks)
  - plan-05 (AuthProvider merge: wire mergeGuestCartToServer + clearLocalCart)
  - header badge (subscribe ['cart'] query key)
  - checkout page (consume useCart + useClearCart)
  - product detail (consume useAddToCart)

tech-stack:
  added:
    - "@tanstack/react-query@^5.0.0"
  patterns:
    - "Dual-backend service wrapper pattern: getAccessToken() branch tại mỗi function call"
    - "Write-through React Query: staleTime Infinity + invalidateQueries on mutation success"
    - "N+1 hydration pattern: GET /cart → Promise.all GET /products/{id} per item (MVP acceptable)"
    - "SSR-safe localStorage: typeof window === 'undefined' guard tại mọi truy cập"

key-files:
  created:
    - sources/frontend/src/hooks/useCart.ts
  modified:
    - sources/frontend/src/services/cart.ts
    - sources/frontend/package.json
    - sources/frontend/package-lock.json

key-decisions:
  - "readCart() SYNC trả [] khi user logged-in — hook layer cung cấp data thật; KHÔNG break callers chưa migrate"
  - "writeCart() no-op cho user path — server là source of truth; tránh stale local override"
  - "N+1 hydration từ product-svc sau mỗi server cart fetch — MVP acceptable, future optimize BE join"
  - "staleTime: Infinity + manual invalidateQueries = write-through behavior, KHÔNG optimistic UI"
  - "parseCartError trong hook file (không trong component) để caller reuse nhưng toast vẫn ở component"
  - "clearLocalCart export riêng cho AuthProvider.logout — clear bất kể login state hiện tại"

patterns-established:
  - "Dual-backend routing: isLoggedIn() = getAccessToken() !== null tại mỗi function call"
  - "Async mutations: addToCart/updateQuantity/removeFromCart/clearCart trả Promise<CartItem[]>"
  - "React Query key: ['cart'] as const — shared giữa useCart query và 4 mutation invalidations"

requirements-completed:
  - STORE-02

duration: 12min
completed: "2026-05-02"
---

# Phase 18 Plan 03: FE Cart Service Dual-Backend + React Query Hooks Summary

**services/cart.ts rewrite thành dual-backend wrapper (guest=localStorage, user=API) với 5 React Query hooks (useCart + 4 mutations) compile clean, backward-compat với callers cũ**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-05-02T15:14:00Z
- **Completed:** 2026-05-02T15:26:21Z
- **Tasks:** 2
- **Files modified:** 4 (cart.ts rewrite, useCart.ts tạo mới, package.json + lock)

## Accomplishments

- Rewrite `services/cart.ts` thành dual-backend: guest path dùng localStorage (6 internal `_local*` helpers) + user path dùng API (6 internal `_server*` async helpers), routing qua `isLoggedIn()` = `getAccessToken() !== null`
- 9 public exports: 3 sync backward-compat (`readCart`, `writeCart`, `clearLocalCart`) + 6 async (`fetchCart`, `addToCart`, `updateQuantity`, `removeFromCart`, `clearCart`, `mergeGuestCartToServer`)
- Tạo `hooks/useCart.ts` với 5 React Query hooks: `useCart` (staleTime: Infinity) + `useAddToCart`, `useUpdateCartItem`, `useRemoveCartItem`, `useClearCart` (invalidateQueries on success)
- `parseCartError` parse `STOCK_SHORTAGE` → "Số lượng vượt quá tồn kho" — export cho consumers reuse
- Cài `@tanstack/react-query@5` (deviation Rule 3 — thiếu dependency, blocking task)
- TypeScript compile clean, ESLint clean, 3 caller files hiện tại không bị break

## Task Commits

1. **Task 1: Rewrite services/cart.ts dual-backend** - `7e69b80` (feat)
2. **Task 2: Tạo useCart React Query hooks** - `449bec4` (feat)

## Files Created/Modified

- `sources/frontend/src/services/cart.ts` — Rewrite hoàn toàn: dual-backend routing, 9 public exports, 12 internal helpers, SSR guards
- `sources/frontend/src/hooks/useCart.ts` — Tạo mới: 5 hooks + parseCartError + CART_QUERY_KEY
- `sources/frontend/package.json` — Thêm `@tanstack/react-query@^5.0.0`
- `sources/frontend/package-lock.json` — Lock file update

## Public API Surface

### services/cart.ts

| Export | Type | Path | Notes |
|--------|------|------|-------|
| `CartItem` | interface | — | type shape (backward-compat) |
| `readCart()` | sync | localStorage only | trả `[]` khi logged-in |
| `writeCart(items)` | sync | localStorage only | no-op khi logged-in |
| `clearLocalCart()` | sync | localStorage only | dùng cho logout |
| `fetchCart()` | async | guest=local, user=API | hook queryFn |
| `addToCart(product, qty?)` | async | guest=local, user=POST /items | mutations |
| `updateQuantity(productId, qty)` | async | guest=local, user=PATCH /items/{id} | mutations |
| `removeFromCart(productId)` | async | guest=local, user=DELETE /items/{id} | mutations |
| `clearCart()` | async | guest=local, user=DELETE /cart | mutations |
| `mergeGuestCartToServer()` | async | POST /cart/merge | Plan 05 AuthProvider |

### hooks/useCart.ts

| Export | Type | Notes |
|--------|------|-------|
| `useCart()` | useQuery | queryKey: ['cart'], staleTime: Infinity |
| `useAddToCart()` | useMutation | invalidate on success |
| `useUpdateCartItem()` | useMutation | invalidate on success |
| `useRemoveCartItem()` | useMutation | invalidate on success |
| `useClearCart()` | useMutation | invalidate on success |
| `parseCartError(err)` | function | STOCK_SHORTAGE → vi message |
| `CART_QUERY_KEY` | const | `['cart'] as const` |
| `CartErrorContext` | type | shortageItems shape |

## Hydration Strategy

Server cart BE trả về chỉ `productId + quantity`. Để render UI (name, price, image, stock), hook `hydrateServerCartItems` gọi `GET /api/products/{id}` cho mỗi item song song (Promise.all).

**Trade-off (N+1):** N items → N+1 network calls (1 GET /cart + N GET /products/{id}). MVP acceptable vì cart thường ít items. Future optimization: BE cart endpoint JOIN với product data.

Khi product-svc fail/soft-delete: placeholder `(Sản phẩm không khả dụng)` + price=0 + stock=0 — render gracefully, không crash.

## React Query Key + Invalidation Pattern

```
CART_QUERY_KEY = ['cart'] as const
useCart: staleTime Infinity → refetch chỉ khi explicit invalidate
Mutations: onSuccess → qc.invalidateQueries({ queryKey: CART_QUERY_KEY })
```

Write-through: sau mỗi mutation success, query key invalidated → React Query tự refetch → UI cập nhật với data thật từ server/localStorage.

## Backward-Compat cho Callers Chưa Migrate

3 callers hiện tại (`cart/page.tsx`, `checkout/page.tsx`, `products/[slug]/page.tsx`) import từ `@/services/cart`. TypeScript compile clean sau rewrite — không breaking change.

**Caveat:** `addToCart`, `updateQuantity`, `removeFromCart`, `clearCart` đã trở thành `async` — callers cần `await`. Plan 04 sẽ refactor sang React Query hooks, lúc đó callers không cần gọi service trực tiếp nữa.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Cài @tanstack/react-query@5**
- **Found during:** Task 2 (tạo useCart.ts) — pre-check
- **Issue:** `@tanstack/react-query` không có trong `package.json` dù plan reference "Phase 7+ đã add". Codebase inspection xác nhận hoàn toàn vắng mặt.
- **Fix:** `npm install @tanstack/react-query@^5.0.0`
- **Files modified:** `package.json`, `package-lock.json`
- **Verification:** Import compile clean, `npx tsc --noEmit` exits 0
- **Committed in:** `7e69b80` (Task 1 commit — package files staged cùng)

---

**Total deviations:** 1 auto-fixed (1 blocking dependency)
**Impact on plan:** Cần thiết để hook layer hoạt động. Không scope creep.

## Issues Encountered

Không có issue ngoài deviation trên.

## Next Phase Readiness

- **Plan 04** (cart page + header badge refactor sang hooks): `useCart`, `useAddToCart`, `useUpdateCartItem`, `useRemoveCartItem`, `useClearCart` sẵn sàng. Import path `@/hooks/useCart`.
- **Plan 05** (AuthProvider merge): `mergeGuestCartToServer()` + `clearLocalCart()` sẵn sàng. Import path `@/services/cart`.
- TypeScript compile clean, ESLint clean — không blocker.

## Threat Flags

Không phát hiện surface mới ngoài threat model đã define trong plan (T-18-10..T-18-13).

---
*Phase: 18-storage-audit-cart-db*
*Completed: 2026-05-02*
