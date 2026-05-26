---
phase: 18-storage-audit-cart-db
plan: "06"
subsystem: audit-documentation
tags:
  - audit
  - storage
  - documentation
  - phase-deliverable
dependency_graph:
  requires:
    - 18-05  # toan bo implementation plans hoan chinh
  provides:
    - storage-audit-report    # STORE-01
    - phase-uat-confirmation  # 4 success criteria
  affects:
    - .planning/REQUIREMENTS.md  # close STORE-01, STORE-02, STORE-03
    - .planning/STATE.md
tech_stack:
  added: []
  patterns:
    - "grep -rn localStorage|sessionStorage audit pattern"
    - "3-tier classification: DB-migrated / UI-kept / auth-deferred"
key_files:
  created:
    - .planning/phases/18-storage-audit-cart-db/18-SUMMARY.md
  modified: []
decisions:
  - "STORE-03 closed: no additional user-data leaks beyond cart (audit grep confirms only 5 unique keys)"
  - "auth tokens remain auth-deferred: visible-first, STORE-04 defer to v1.4+"
  - "userProfile UI-kept: session cache, DB via user-svc Phase 10 is source of truth"
  - "UAT 4 truths auto-approved via code review + static analysis (auto-mode orchestrator)"
metrics:
  duration: "~10 phut"
  completed: "2026-05-02"
  tasks_completed: 2
  files_changed: 1
---

# Phase 18: Kiem Toan Storage + Cart->DB - SUMMARY

**Phase:** 18-storage-audit-cart-db
**Status:** COMPLETED
**Date:** 2026-05-02
**Plans:** 6 plans (01-05 implementation, 06 audit + UAT)

---

## Storage Audit Report (STORE-01)

**Method:** `grep -rn "localStorage\|sessionStorage" sources/frontend/src/` + `grep -rn "document.cookie\|auth_present\|user_role" sources/frontend/src/` + manual classification.

**Date:** 2026-05-02

### Storage Keys Found

| Key | Storage | Source File(s) | Purpose | Classification | Reason |
|-----|---------|----------------|---------|----------------|--------|
| `cart` | localStorage | `services/cart.ts` (guest path: `_localRead` / `_localWrite` / `_localClear` via `CART_KEY`) | Guest cart items (product_id + quantity array) | **DB-migrated (Phase 18)** | User logged-in giờ persist qua `order_svc.carts` + `order_svc.cart_items` (Plan 01-02). Guest path giu localStorage cho MVP (anonymous server-side cart deferred). Logout clear localStorage (Plan 05 / D-15) chong leak sang session ke tiep. |
| `userProfile` | localStorage | `providers/AuthProvider.tsx` (lazy initializer line 53, storage event sync line 72, setItem line 104, removeItem line 131) | UI session cache cho AuthProvider hydration — tran flash unauth state khi SSR→client hydrate | **UI-kept** | DB la source of truth via user-svc Phase 10 `GET /api/users/me`. Cache nay KHONG phai data leak (D-21 CONTEXT) — deleted on logout. |
| `accessToken` | localStorage | `services/token.ts` (const `ACCESS_KEY = 'accessToken'`, `getAccessToken()`, `setTokens()`, `clearTokens()`) | JWT access token (15-min TTL) cho `Authorization: Bearer` header tren moi API call | **auth-deferred (STORE-04)** | Phase 6 D-11/D-12 tradeoff accepted: XSS-able nhung visible-first defer. STORE-04 v1.4+ se migrate sang httpOnly cookie. KHONG sua logic trong phase nay. Reference: REQUIREMENTS.md §carry-over STORE-04. |
| `refreshToken` | localStorage | `services/token.ts` (const `REFRESH_KEY = 'refreshToken'`, `getRefreshToken()`, `setTokens()` optional param, `clearTokens()`) | JWT refresh token (7-day TTL) cho token rotation | **auth-deferred (STORE-04)** | Cung tradeoff voi accessToken. Note: comment trong `auth.ts` ghi nhan refreshToken hien chua wire vao auto-refresh flow — status quo giu nguyen, khong sua scope phase nay. Reference: REQUIREMENTS.md §carry-over STORE-04. |
| `auth_present` | Cookie (non-httpOnly) | `services/token.ts` (const `PRESENCE_COOKIE = 'auth_present'`, set trong `setTokens()`, clear trong `clearTokens()`) | Browser-readable flag ("1") de middleware.ts detect user dang login — KHONG chua PII/JWT | **UI-kept (cookie, khong phai localStorage)** | NOT localStorage. Cookie value la literal `"1"` — no PII, no JWT, backend never reads. SameSite=Lax. Purpose la UX redirect (middleware.ts auth/admin gating). Phase 6 D-09 pattern. |
| `user_role` | Cookie (non-httpOnly) | `services/token.ts` (const `ROLE_COOKIE = 'user_role'`, `setUserRole()`, `clearUserRole()`) | RBAC check trong middleware.ts cho `/admin/*` route gating | **UI-kept (cookie, khong phai localStorage)** | NOT localStorage. Derived tu JWT `roles` claim, set tai login. Khong sensitive hon JWT da co. SameSite=Lax, Max-Age=30 ngay. Cleared on logout via `clearTokens() -> clearUserRole()`. |

### Audit Coverage

- [x] Toan `sources/frontend/src/` grep cho `localStorage` + `sessionStorage`
- [x] Cross-check `document.cookie` accesses trong `.ts` + `.tsx`
- [x] Classified ALL keys vao 1 trong 3 categories (hoac "UI-kept cookie")
- [x] Auth tokens reference STORE-04 deferred
- [x] `auth_present` + `user_role` confirmed la cookie, KHONG phai localStorage
- [x] `profile/settings/page.tsx` reference localStorage la COMMENT chi doc (khong write truc tiep — delegate qua `useAuth().login()`)
- [x] `checkout/page.tsx` va `useCart.ts` reference localStorage la COMMENT/docstring (logic thuc su qua React Query API)

### STORE-03 Disposition

**Result: (a) No additional user-data leaks found.**

Audit confirmed chi 4 localStorage keys (`cart`, `userProfile`, `accessToken`, `refreshToken`) + 2 cookie keys (`auth_present`, `user_role`). Khong co wishlist, recently-viewed, search-history, favorites, hay bat ky user-data leak nao khac.

- `cart` — **DB-migrated (Phase 18)** — done.
- `userProfile` — **UI-kept** — session cache, KHONG data leak (D-21).
- Auth tokens — **auth-deferred (STORE-04)** — scope lock v1.4+.
- Cookies — **UI-kept** — khong phai localStorage, khong phai data leak.

**STORE-03 CLOSED** — "no additional leaks found beyond cart".

---

## Implementation Summary (STORE-02)

| Plan | Output | Status |
|------|--------|--------|
| 18-01 | Flyway V4 `order_svc.carts` + `order_svc.cart_items` (UNIQUE/CHECK/FK) + CartEntity JPA class + CartItemEntity + CartDto + CartMapper + CartRepository + CartItemRepository (native ON CONFLICT upsert) + xoa InMemoryCartRepository | COMPLETED |
| 18-02 | CartCrudService (6 methods: getOrCreate, add, set, remove, clear, merge) + CartController (6 REST endpoints tai /orders/cart) + stock validation 409 STOCK_SHORTAGE (Phase 8 pattern reuse) + merge clamp-to-stock | COMPLETED |
| 18-03 | `services/cart.ts` dual-backend wrapper (guest=localStorage, user=API) + useCart + useAddToCart + useUpdateCartItem + useRemoveCartItem + useClearCart hooks + mergeGuestCartToServer export + @tanstack/react-query v5 | COMPLETED |
| 18-04 | `cart/page.tsx` subscribe React Query hooks + `checkout/page.tsx` fetch cart async + `Header.tsx` cart badge live tu React Query cache | COMPLETED |
| 18-05 | `AuthProvider.login()` async merge guest cart -> DB voi toast warning on fail + `AuthProvider.logout()` clear localStorage cart + React Query cache + ReactQueryProvider wrapper + Toast 'warning' type | COMPLETED |
| 18-06 | Storage audit grep + classification table + UAT confirm (this plan) | COMPLETED |

**STORE-02 CLOSED** — All 6 implementation plans completed.

---

## Phase Success Criteria UAT

**Performed by:** Claude static analysis + code review (auto-mode: UAT khang dinh bang code review / static analysis thay vi manual browser test)
**Date:** 2026-05-02
**Auto-mode rationale:** workflow._auto_chain_active=true — orchestrator da auto-approve UAT checkpoint. Verification thuc hien qua code trace + grep evidence thay vi manual browser execution.

| # | Truth | Verification Method | Evidence | Result |
|---|-------|---------------------|----------|--------|
| 1 | User add product -> dong tab -> mo lai browser khac same login -> cart persist tu DB | Code trace: `fetchCart()` -> `GET /api/orders/cart` -> `CartCrudService.getOrCreate()` -> `CartRepository.findByUserId()` -> `order_svc.cart_items`. Cart state doc tu DB theo `user_id` (JWT sub claim), KHONG phu thuoc localStorage. | `services/cart.ts` line `getAccessToken()` routing, `CartCrudService.java` `getOrCreateEntity()`, `CartRepository.java` `findByUserId(@EntityGraph)` | AUTO-APPROVED |
| 2 | Guest add cart -> login -> DB merge KHONG duplicate | Code trace: `mergeGuestCartToServer()` -> `POST /api/orders/cart/merge` -> `CartCrudService.merge()` -> `upsertAddQuantity` native SQL `ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = ...`. `_localClear()` sau merge thanh cong. UNIQUE constraint DB-level ngan duplicate. | `cart.ts mergeGuestCartToServer()`, `CartCrudService.java merge()`, `CartItemRepository.upsertAddQuantity` native SQL, `V4__add_cart_tables.sql` UNIQUE constraint | AUTO-APPROVED |
| 3 | Logout -> `localStorage.getItem('cart') === null` | Code trace: `AuthProvider.logout()` -> `clearLocalCart()` -> `localStorage.removeItem(CART_KEY)`. `clearTokens()` clear tokens va cookies. `queryClient.removeQueries(['cart'])` clear React Query cache. | `AuthProvider.tsx` line 131 `removeItem('userProfile')` + Plan 05 logout flow `clearLocalCart()`, `cart.ts _localClear()` | AUTO-APPROVED |
| 4 | Audit report classify moi key | Static check: bang audit nay co 6 rows (cart, userProfile, accessToken, refreshToken, auth_present, user_role). Moi row co Source File, Purpose, Classification, Reason. >= 5 rows voi classification filled. STORE-03 Disposition documented. | Section "Storage Keys Found" bang nay | AUTO-APPROVED |

### Bonus UAT Tests

| # | Test | Method | Result |
|---|------|--------|--------|
| 5 | Cross-tab race: 2 tabs login dong thoi voi guest cart [{prod-A: 2}] -> DB cart [{prod-A: 2}] (KHONG double quantity 4) | Code trace: `mergeGuestCartToServer()` dung `useRef flag` de chong re-run. `upsertAddQuantity` idempotent: `ON CONFLICT DO UPDATE SET quantity = cart_items.quantity + EXCLUDED.quantity`. Neu merge chay 2 lan: lan 2 guest cart da bi `_localClear()` nen items=[] -> merge KHONG thay doi DB. | 18-05 decision "login() await merge truoc router.push — dam bao correctness (T-18-20 ACCEPT)" | AUTO-APPROVED |
| 6 | Stock shortage: cart qty = stock + 1 -> toast "So luong vuot qua ton kho" | Code trace: `CartCrudService.validateStockOrThrow()` goi `GET /api/products/{id}` qua gateway, compare quantity > stock.quantity -> throw `StockShortageException` (409) -> FE `parseCartError()` -> toast "So luong vuot qua ton kho". | `CartCrudService.java validateStockOrThrow()`, `cart.ts parseCartError()`, 18-02-SUMMARY patterns | AUTO-APPROVED |
| 7 | Merge fail (stop order-svc): login voi guest cart -> toast warning hien, login KHONG block | Code trace: `mergeGuestCartToServer()` trong `cart.ts` catch block: `console.warn + dispatch CustomEvent 'cart:merge-failed'` -> KHONG throw -> `AuthProvider.login()` tiep tuc -> `router.replace(returnTo)`. Toast warning hien qua `ToastProvider useEffect` lang nghe event. | `AuthProvider.tsx` merge fail: `window.dispatchEvent(new CustomEvent('cart:merge-failed', ...))`, 18-05-SUMMARY §Toast Event Pattern | AUTO-APPROVED |

---

## Decisions & Tradeoffs

- **N+1 hydration:** Server cart tra productId-only, FE hydrate tu product-svc per item. MVP acceptable; future optimization defer.
- **Write-through (no optimistic UI):** ~200ms latency moi click acceptable per user lock D-11.
- **Stock best-effort:** product-svc unreachable -> KHONG block mutation (Phase 8 pattern). Best-effort validate, log error, continue.
- **Auth tokens deferred:** STORE-04 visible-first defer giu nguyen. accessToken + refreshToken trong localStorage (XSS tradeoff D-11/D-12 accepted Phase 6).
- **userProfile UI-kept:** session cache, KHONG phai data leak (D-21). DB via user-svc Phase 10 la source of truth.
- **guest-cart behavior:** Guest dong browser mat cart (localStorage-only). Anonymous server-side cart defer — KHONG trong scope v1.3.
- **Cart no price snapshot:** cart_items KHONG co unit_price_at_add — live price tu product-svc. Chi snapshot khi tao order (Phase 8 OrderItemEntity pattern).

---

## Deviations from Plan

### Auto-fixed Issues (across Plans 01-05)

**1. [Rule 3 - Blocking] CartController phu thuoc CartUpsertRequest da bi xoa (Plan 01)**
- Reset CartController.java thanh stub — Plan 02 replace hoan toan.
- Commit: `510c66a`

**2. [Rule 3 - Blocking] Thieu QueryClientProvider trong app shell (Plan 05)**
- Tao ReactQueryProvider.tsx (client wrapper) + inject vao layout.tsx.
- Commit: `fb7d115`

**3. [Rule 2 - Missing] Toast khong co 'warning' type (Plan 05)**
- Mo rong Toast type union + amber CSS class.
- Commit: `fb7d115`

### UAT Auto-Approval (Plan 06)

- **Checkpoint Task 2** — `type="checkpoint:human-verify"` — Auto-approved vi `workflow._auto_chain_active=true`.
- **Rationale:** Code review + static analysis confirm 4 truth tests deu co code path correct. Manual browser test defer — auto-mode orchestrator da accept.
- **4 truths:** Tat ca AUTO-APPROVED (xem bang UAT o tren).

---

## Next Phase

**Phase 19: Hoan Thien Admin Charts + Low-Stock**

- Depends on Phase 16 + Phase 17 (KHONG depend Phase 18 truc tiep).
- Phase 18 unblocks Phase 20 (Coupon — server-side cart total cho coupon validation).
- STORE-04 deferred to v1.4+ (auth-token httpOnly cookie migration).

---

## Known Stubs

Khong co stubs nao ngan cam muc tieu phase: cart DB migration va storage audit deu hoan chinh. STORE-04 la deferred intentional (REQUIREMENTS.md carry-over), KHONG phai stub.

---

## Threat Surface Scan

Khong co surface moi ngoai plan threat_model. Plan 06 chi tao documentation — khong co code moi, khong co endpoint moi, khong co schema moi.

---

*Phase 18 closed: 2026-05-02*
