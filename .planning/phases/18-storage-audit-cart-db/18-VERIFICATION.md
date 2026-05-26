---
phase: 18-storage-audit-cart-db
verified: 2026-05-02T12:00:00Z
status: human_needed
score: 6/7
overrides_applied: 0
human_verification:
  - test: "Chạy `mvn compile` tại order-service để xác nhận backend compile không lỗi"
    expected: "BUILD SUCCESS — không có compilation error cho CartEntity, CartItemEntity, CartMapper, CartDto, CartItemDto, CartRepository, CartItemRepository, CartCrudService, CartController"
    why_human: "mvn không có trong PATH của môi trường verification; không thể chạy compile tự động"
  - test: "Manual browser test — Cart persist cross-session (Truth #1): Login user A, add 3 SP, đóng tab, login lại user A ở browser khác → verify cart còn đủ 3 SP"
    expected: "Cart hiển thị đúng 3 sản phẩm từ DB; DevTools localStorage.getItem('cart') === null"
    why_human: "Requires full stack running với DB + API; không thể verify bằng static analysis"
  - test: "Manual browser test — Guest→DB merge không duplicate (Truth #2): Guest add 2 SP, login user mới → verify 2 SP trong cart, không bị duplicate"
    expected: "CartItems trong DB có đúng 2 rows; localStorage.getItem('cart') === null sau merge"
    why_human: "Requires running stack + DB query verification"
  - test: "Manual browser test — Logout clear localStorage (Truth #4): Login, add SP, logout → localStorage.getItem('cart') === null"
    expected: "null — clearLocalCart() đã xóa CART_KEY"
    why_human: "Requires browser interaction để verify thực tế"
---

# Phase 18: Kiểm Toán Storage + Cart→DB — Verification Report

**Phase Goal:** Giỏ hàng của người dùng persist trên server (không mất khi clear browser), và toàn bộ data user-sensitive không còn rò rỉ qua localStorage
**Verified:** 2026-05-02T12:00:00Z
**Status:** human_needed
**Re-verification:** Không — initial verification

---

## Goal Achievement

### Observable Truths (từ ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | Người dùng add SP vào giỏ, đóng tab, mở lại → giỏ hàng vẫn còn đủ (persist qua server, không phụ thuộc localStorage) | ? HUMAN_NEEDED | Code path verified: `fetchCart()` → `isLoggedIn()=true` → `_serverGet()` → `httpGet('/api/orders/cart')` → `CartController.getCart()` → `CartCrudService.getOrCreateCart()` → `CartRepository.findByUserId()` (EntityGraph fetch join) → DB. Logic đúng nhưng cần browser test để xác nhận end-to-end |
| 2 | Guest add vào giỏ → login → giỏ hàng merge đúng, không bị duplicate item | ? HUMAN_NEEDED | Code path verified: `AuthProvider.login()` → `mergeGuestCartToServer()` → `POST /api/orders/cart/merge` → `CartCrudService.mergeFromGuest()` → `upsertAddQuantity()` native SQL `ON CONFLICT DO UPDATE` + `_localClear()` sau merge. UNIQUE constraint `(cart_id, product_id)` đảm bảo no duplicate. Logic đúng nhưng cần browser test |
| 3 | Audit report (SUMMARY.md) liệt kê tất cả localStorage/sessionStorage keys được classify | ✓ VERIFIED | `18-SUMMARY.md` §Storage Audit Report có đủ 6 rows: `cart` (DB-migrated), `userProfile` (UI-kept), `accessToken` (auth-deferred), `refreshToken` (auth-deferred), `auth_present` (UI-kept cookie), `user_role` (UI-kept cookie). Mỗi row có Source File, Purpose, Classification, Reason. STORE-03 Disposition documented: "no additional leaks found beyond cart". Grep cross-check: không có `sessionStorage` usage nào trong codebase |
| 4 | Cart localStorage không chứa dữ liệu user sau khi logout | ? HUMAN_NEEDED | Code path verified: `AuthProvider.logout()` → `clearLocalCart()` → `cart.ts._localClear()` → `window.localStorage.removeItem(CART_KEY)` (CART_KEY = 'cart'). Logic đúng nhưng cần browser test để xác nhận thực tế |

**Score:** 1/4 truths VERIFIED programmatically, 3/4 HUMAN_NEEDED (code path confirmed đúng, cần browser validation)

---

### Derived Must-Haves từ User-Defined Checks

| # | Check | Status | Evidence |
|---|-------|--------|---------|
| 1 | Cart persists qua sessions (DB-backed cho user, không còn localStorage cho user path) | ✓ VERIFIED | `fetchCart()` route: `isLoggedIn() ? _serverGet() : _localRead()`. `_serverGet()` calls `GET /api/orders/cart`. CartController → CartCrudService → CartRepository với `@EntityGraph` fetch. Không có fallback localStorage cho user path |
| 2 | Guest cart merge vào DB khi login (no duplicate) | ✓ VERIFIED (code) | `upsertAddQuantity` native SQL: `ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = order_svc.cart_items.quantity + EXCLUDED.quantity`. UNIQUE constraint V4 migration. `_localClear()` sau merge thành công |
| 3 | Logout clear localStorage cart | ✓ VERIFIED (code) | `AuthProvider.logout()` line 135: `clearLocalCart()` → `_localClear()` → `localStorage.removeItem('cart')`. `queryClient.removeQueries({ queryKey: ['cart'] })` xóa React Query cache |
| 4 | Storage audit table classify mọi key localStorage/sessionStorage | ✓ VERIFIED | 18-SUMMARY.md §Storage Audit Report: 6 rows. Grep confirm: 0 `sessionStorage` usage; 4 unique `localStorage` keys (`cart`, `userProfile`, `accessToken`, `refreshToken`) + 2 cookies (`auth_present`, `user_role`). Toàn bộ classified |
| 5 | STORE-01, STORE-02, STORE-03 đóng trong REQUIREMENTS.md | ✓ VERIFIED | REQUIREMENTS.md lines 45-47: `[x] STORE-01`, `[x] STORE-02`, `[x] STORE-03` — tất cả marked COMPLETED với reference Phase 18 plans. Coverage Map §STORE-01/02/03 → Phase 18 |
| 6 | Không còn cart-stub cũ (InMemoryCartRepository xóa, OrderCrudService không có cart legacy methods) | ✓ VERIFIED | `find sources/backend/order-service/src -name "InMemoryCartRepository.java"` → 0 results. `OrderCrudService.java` grep "cart/Cart": chỉ có 1 comment reference D-06 về productName snapshot (không phải cart logic). InMemoryCartRepository đã bị xóa hoàn toàn |
| 7 | Backend mvn compile pass; FE TypeScript compile pass | PARTIAL (FE VERIFIED, BE needs human) | FE: `npx tsc --noEmit` completed với exit 0 (no output = no errors). BE: mvn không có trong PATH — cần human verify |

**Score tổng hợp: 6/7** (STORE-01/02/03 closed, InMemory deleted, FE TS clean, audit complete; BE compile và 3 browser tests cần human)

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `sources/backend/order-service/src/main/resources/db/migration/V4__add_cart_tables.sql` | Flyway V4 schema carts + cart_items | ✓ VERIFIED | Tồn tại, 22 lines. `CREATE TABLE order_svc.carts` với `user_id UNIQUE`, `CREATE TABLE order_svc.cart_items` với `UNIQUE(cart_id, product_id)` + `CHECK(quantity > 0)` + FK + cascade. Index `idx_cart_items_cart_id` |
| `...domain/CartEntity.java` | JPA entity @Table(carts) | ✓ VERIFIED | `@Entity @Table(name="carts", schema="order_svc")`. `@OneToMany orphanRemoval=true`, `@Column unique=true` cho userId. `create()` factory method |
| `...domain/CartItemEntity.java` | JPA entity @Table(cart_items) | ✓ VERIFIED | Tồn tại trong domain/ |
| `...domain/CartMapper.java` | CartEntity → CartDto mapping | ✓ VERIFIED | 16 lines. `toDto(CartEntity)` stream items → `CartItemDto`. Substantive, không stub |
| `...repository/CartRepository.java` | JPA repo với findByUserId + @EntityGraph | ✓ VERIFIED | `@EntityGraph(attributePaths="items")` fetch join tránh LazyInitializationException. `findByUserId()` |
| `...repository/CartItemRepository.java` | Repo với upsertAddQuantity native SQL | ✓ VERIFIED | `@Modifying @Query(nativeQuery=true)`: `INSERT ... ON CONFLICT DO UPDATE SET quantity = ...`. Substantive |
| `...service/CartCrudService.java` | 6 methods: getOrCreate, add, set, remove, clear, merge | ✓ VERIFIED | 226 lines. 6 `@Transactional` methods. Stock validation via RestTemplate. Race condition handling với DataIntegrityViolationException retry. clampToStock cho merge |
| `...web/CartController.java` | 6 REST endpoints tại /orders/cart | ✓ VERIFIED | 81 lines. 6 endpoints: GET, POST /items, PATCH /items/{id}, DELETE /items/{id}, DELETE, POST /merge. X-User-Id header pattern. ApiResponse wrapper |
| `sources/frontend/src/services/cart.ts` | Dual-backend wrapper (guest=localStorage, user=API) | ✓ VERIFIED | 253 lines. `isLoggedIn()` routing. 5 async public functions (`fetchCart`, `addToCart`, `updateQuantity`, `removeFromCart`, `clearCart`). `mergeGuestCartToServer()` + `clearLocalCart()` exported. SSR-safe guards |
| `sources/frontend/src/hooks/useCart.ts` | React Query hooks: useCart + 4 mutations | ✓ VERIFIED | 103 lines. `useCart` với `useQuery(['cart'])`. `useAddToCart`, `useUpdateCartItem`, `useRemoveCartItem`, `useClearCart` với `onSuccess invalidateQueries`. `parseCartError()` xử lý STOCK_SHORTAGE 409 |
| `sources/frontend/src/providers/AuthProvider.tsx` | login() async merge + logout() clear cart | ✓ VERIFIED | 147 lines. `login()`: `await mergeGuestCartToServer()`, dispatch 'cart:merge-failed' nếu fail, `queryClient.invalidateQueries(['cart'])`. `logout()`: `clearLocalCart()`, `queryClient.removeQueries(['cart'])` |
| `sources/frontend/src/providers/ReactQueryProvider.tsx` | QueryClientProvider wrapper | ✓ VERIFIED | 46 lines. `QueryClient` với `staleTime: 60*1000`. `getQueryClient()` singleton pattern cho browser |
| `.planning/phases/18-storage-audit-cart-db/18-SUMMARY.md` | Audit table + UAT results | ✓ VERIFIED | Tồn tại. 6-row audit table, STORE-03 disposition, 4 UAT truths + 3 bonus tests |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `cart/page.tsx` | `useCart` / `useUpdateCartItem` / `useRemoveCartItem` | import từ `@/hooks/useCart` | ✓ WIRED | Lines 10-14: import. Lines 19-21: destructure `data: cartItems`, `updateMutation`, `removeMutation`. Lines 26, 38: `mutate()` calls |
| `checkout/page.tsx` | `useCart` / mutations | import từ `@/hooks/useCart` | ✓ WIRED | Lines 14-18: import. Lines 40-42: usage |
| `Header.tsx` | `useCart` | import từ `@/hooks/useCart` | ✓ WIRED | Line 9: import. Line 16-17: `data: cartItems`, `cartCount = cartItems.reduce(...)` |
| `useCart.ts` | `fetchCart`, `cartServiceAdd`, etc. | import từ `@/services/cart` | ✓ WIRED | Lines 18-26: import. `queryFn: fetchCart`, `mutationFn: cartServiceAdd` |
| `AuthProvider.tsx` | `mergeGuestCartToServer`, `clearLocalCart` | import từ `@/services/cart` | ✓ WIRED | Line 23: import. Line 108: `await mergeGuestCartToServer()`. Line 135: `clearLocalCart()` |
| `AuthProvider.tsx` | `useQueryClient` | import từ `@tanstack/react-query` | ✓ WIRED | Line 21: import. Line 121: `queryClient.invalidateQueries`. Line 136: `queryClient.removeQueries` |
| `layout.tsx` | `ReactQueryProvider` NGOÀI `AuthProvider` | composition trong layout | ✓ WIRED | Lines 31-37: `<ReactQueryProvider><AuthProvider>...</AuthProvider></ReactQueryProvider>` — đúng thứ tự nesting |
| `cart.ts._serverMerge()` | `POST /api/orders/cart/merge` | `httpPost` | ✓ WIRED | Line 164: `httpPost<ServerCartDto>('/api/orders/cart/merge', { items })` |
| `CartController.merge()` | `CartCrudService.mergeFromGuest()` | method call | ✓ WIRED | Line 77: `cartService.mergeFromGuest(userId, body)` |
| `CartCrudService.mergeFromGuest()` | `CartItemRepository.upsertAddQuantity()` | native SQL call | ✓ WIRED | Line 148: `cartItemRepository.upsertAddQuantity(newItemId, cart.id(), item.productId(), delta)` |

---

## Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `cart/page.tsx` | `cartItems` | `useCart()` → `fetchCart()` → `_serverGet()` → `GET /api/orders/cart` → DB | DB query: `CartRepository.findByUserId()` với EntityGraph | ✓ FLOWING |
| `Header.tsx` | `cartItems`, `cartCount` | `useCart()` → same chain | DB query cho user; localStorage cho guest | ✓ FLOWING |
| `AuthProvider.tsx` | merge result | `mergeGuestCartToServer()` → `POST /api/orders/cart/merge` → `upsertAddQuantity` native SQL | Native SQL INSERT/UPDATE | ✓ FLOWING |

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|---------|
| STORE-01 | 18-06 | Audit toàn FE codebase, classify localStorage/sessionStorage | ✓ SATISFIED | 18-SUMMARY.md §Storage Audit Report: 6 rows, mọi key classified. grep confirm 0 sessionStorage |
| STORE-02 | 18-01 to 18-05 | Migrate cart từ localStorage sang DB | ✓ SATISFIED | V4 schema + CartEntity/Repo/Service/Controller + services/cart.ts dual-backend + 3 consumer components + AuthProvider lifecycle |
| STORE-03 | 18-06 | Migrate user-data leaks khác hoặc giải thích giữ lại | ✓ SATISFIED | 18-SUMMARY.md §STORE-03 Disposition: "No additional user-data leaks found." Audit xác nhận chỉ có cart (migrated), userProfile (UI cache), auth tokens (STORE-04 deferred) |

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `services/cart.ts` | 51-53 | `return []` trong `_localRead()` | ℹ️ Info | Legitimate: SSR guard (`typeof window === 'undefined'`) + JSON parse fail fallback. KHÔNG phải stub — data flows từ `localStorage.getItem(CART_KEY)` |
| `services/cart.ts` | 180 | `if (isLoggedIn()) return []` trong `readCart()` | ℹ️ Info | Intentional design: logged-in users dùng `useCart()` hook async, không dùng `readCart()` sync. Comment documentation đầy đủ |
| `services/cart.ts` | 220 | `return []` trong `clearCart()` guest path | ℹ️ Info | Correct: sau khi clear localStorage cart rỗng = `[]`. Không phải stub |
| `providers/AuthProvider.tsx` | 51,56 | `return null` trong useState initializer | ℹ️ Info | Legitimate SSR safety pattern — server renders null, client hydrates từ localStorage |

**Không có blocker anti-patterns.** Tất cả `return []`/`return null` đều có lý do hợp lệ và không phải stub rỗng.

---

## Behavioral Spot-Checks (Static/Code Level)

| Behavior | Check | Result | Status |
|----------|-------|--------|--------|
| FE TypeScript compile clean | `npx tsc --noEmit` trong frontend/ | Exit 0, no output | ✓ PASS |
| `InMemoryCartRepository` bị xóa | `find src -name "InMemoryCartRepository.java"` | 0 results | ✓ PASS |
| No `sessionStorage` usage trong FE | `grep -rn "sessionStorage" sources/frontend/src/` | 0 results | ✓ PASS |
| CartRepository DB query (không static return) | `findByUserId()` với `@EntityGraph` → JPA query | Real DB query | ✓ PASS |
| `upsertAddQuantity` native SQL (không static return) | `INSERT ... ON CONFLICT DO UPDATE` | Real DB write | ✓ PASS |
| `clearLocalCart()` wired vào logout | `AuthProvider.logout()` line 135: `clearLocalCart()` | Wired | ✓ PASS |
| `mergeGuestCartToServer()` wired vào login | `AuthProvider.login()` line 108: `await mergeGuestCartToServer()` | Wired | ✓ PASS |
| ReactQueryProvider NGOÀI AuthProvider | `layout.tsx` wrapping order | Correct nesting | ✓ PASS |
| BE mvn compile | Requires `mvn compile` | mvn not in PATH | ? SKIP (→ human) |

---

## Human Verification Required

### 1. Backend Compile Check

**Test:** Chạy `mvn compile` tại `sources/backend/order-service/`
**Expected:** BUILD SUCCESS — CartEntity, CartItemEntity, CartDto, CartItemDto, CartMapper, CartRepository, CartItemRepository, CartCrudService, CartController đều compile không lỗi. Đặc biệt verify không có unresolved import nào cho các class Phase 18 mới.
**Why human:** mvn không có trong PATH của môi trường verification

### 2. Cart Persist Cross-Browser (Truth #1)

**Test:**
1. Chrome browser A: login user, add 3 sản phẩm vào cart
2. Đóng hoàn toàn browser A
3. Firefox hoặc Chrome incognito (browser B): login cùng user
4. Vào `/cart`
5. DevTools console: `localStorage.getItem('cart')`

**Expected:** Thấy 3 SP trong cart (từ DB); `localStorage.getItem('cart')` === null
**Why human:** Requires full stack running (Next.js + order-svc + DB)

### 3. Guest→DB Merge Không Duplicate (Truth #2)

**Test:**
1. Browser incognito: không login, add 2 SP vào cart
2. Verify: `localStorage.getItem('cart')` có items
3. Login user mới (chưa có DB cart)
4. Vào `/cart`
5. DevTools: `localStorage.getItem('cart')`
6. Optionally: DB query `SELECT product_id, quantity FROM order_svc.cart_items WHERE cart_id = ...`

**Expected:** 2 SP trong cart, không duplicate; `localStorage.getItem('cart')` === null sau merge
**Why human:** Requires running stack + DB access

### 4. Logout Clear Cart LocalStorage (Truth #4)

**Test:**
1. Login bất kỳ user, add 1 SP
2. Xác nhận: `localStorage.getItem('cart')` === null (logged-in user dùng DB, không phải localStorage)
3. Logout
4. DevTools: `localStorage.getItem('cart')` — phải null
5. Login user khác — verify cart trống không thấy cart của user trước

**Expected:** `null` tại mọi điểm; không có cart leak giữa users
**Why human:** Requires browser interaction

---

## Gaps Summary

Không có gaps blocking goal achievement. Tất cả code artifacts verified substantive và wired. Các human_needed items là browser/runtime validation — code path đã confirmed đúng qua static analysis.

**STORE-04 (auth token httpOnly cookie migration):** Intentional defer đến v1.4+, documented trong REQUIREMENTS.md §carry-over. Không phải gap của Phase 18.

---

## Tóm Tắt Kết Quả

**Những gì VERIFIED:**
- V4 Flyway schema: `carts` + `cart_items` với UNIQUE constraints và FK cascade
- CartEntity, CartItemEntity, CartMapper, CartDto, CartRepository, CartItemRepository — tất cả substantive, không stub
- CartCrudService: 6 methods đầy đủ (getOrCreate, add, set, remove, clear, merge) với stock validation và race condition handling
- CartController: 6 endpoints mapping đúng vào service methods
- `services/cart.ts`: dual-backend routing đúng (guest → localStorage, user → API)
- `useCart.ts` hooks: React Query wired, invalidateQueries trên mọi mutation
- Consumers: `cart/page.tsx`, `checkout/page.tsx`, `Header.tsx` đều import và sử dụng hooks
- `AuthProvider.login()`: `await mergeGuestCartToServer()` + invalidate cache
- `AuthProvider.logout()`: `clearLocalCart()` + `removeQueries(['cart'])`
- `ReactQueryProvider` bọc ngoài `AuthProvider` trong `layout.tsx`
- `InMemoryCartRepository` đã bị xóa hoàn toàn
- FE TypeScript compile: clean (npx tsc --noEmit exit 0)
- STORE-01/02/03 closed trong REQUIREMENTS.md
- Audit table: 6 keys classified, STORE-03 disposition documented
- 0 sessionStorage usage trong toàn FE codebase

**Cần human validate:**
- BE `mvn compile` (mvn không trong PATH)
- 3 browser UAT tests (stack cần running)

---

*Verified: 2026-05-02T12:00:00Z*
*Verifier: Claude (gsd-verifier)*
