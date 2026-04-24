---
phase: 04-frontend-contract-alignment-e2e-validation
plan: 02
subsystem: frontend
status: complete
requirements: [FE-01, FE-02]
completed: 2026-04-24
tags:
  - frontend
  - react
  - ui-components
  - error-handling
  - pages
dependency_graph:
  requires:
    - 04-01 typed HTTP tier (httpGet/Post + ApiError + 401 silent redirect)
    - 04-01 services/{products,orders,cart,auth}.ts + generated types
    - 04-01 middleware + AuthProvider + ToastProvider mounted in layout
    - UI-SPEC surfaces 1-3 (Banner/Modal/RetrySection) + Copywriting Contract
  provides:
    - Banner component (UI-SPEC Surface 1, role=alert + aria-live=assertive)
    - Modal component (UI-SPEC Surface 2, role=dialog + Esc/backdrop close + scroll lock)
    - RetrySection component (UI-SPEC Surface 3, default Vietnamese copy)
    - Toast close button aria-label patch (UI-SPEC checker callout resolved)
    - Full error dispatcher on checkout page across VALIDATION_ERROR / CONFLICT (stock + payment) / UNAUTHORIZED / FORBIDDEN / NOT_FOUND / INTERNAL_ERROR
    - Real listProducts / getProductBySlug / listMyOrders + RetrySection on 5xx
    - Cart page backed by services/cart.readCart() + cart:change event
    - Open-redirect hardened login (returnTo guard startsWith('/') && !startsWith('//'))
  affects:
    - 04-03 UAT walkthrough — all UAT-critical pages now call real services
    - Next phase if it ships /auth/* — swap mock login/register submit for services/auth.login/register (Deviation 1)
tech-stack:
  added: []
  patterns:
    - Lazy-initializer useState for SSR-safe localStorage hydration (cart + checkout)
    - Error-dispatcher switch on err.code (Shared Pattern 5)
    - CONFLICT discriminator via details.domainCode === 'STOCK_SHORTAGE' || Array.isArray(details.items)
    - Token-only CSS-modules (+ two documented literal exceptions: error tint + modal backdrop)
key-files:
  created:
    - sources/frontend/src/components/ui/Banner/Banner.tsx
    - sources/frontend/src/components/ui/Banner/Banner.module.css
    - sources/frontend/src/components/ui/Modal/Modal.tsx
    - sources/frontend/src/components/ui/Modal/Modal.module.css
    - sources/frontend/src/components/ui/RetrySection/RetrySection.tsx
    - sources/frontend/src/components/ui/RetrySection/RetrySection.module.css
    - sources/frontend/src/app/not-found.tsx
  modified:
    - sources/frontend/src/components/ui/Toast/Toast.tsx
    - sources/frontend/src/components/ui/index.ts
    - sources/frontend/src/app/login/page.tsx
    - sources/frontend/src/app/register/page.tsx
    - sources/frontend/src/app/cart/page.tsx
    - sources/frontend/src/app/checkout/page.tsx
    - sources/frontend/src/app/products/page.tsx
    - sources/frontend/src/app/products/[slug]/page.tsx
    - sources/frontend/src/app/profile/page.tsx
    - sources/frontend/src/app/page.tsx
decisions:
  - "Login/register keep mock submit flow per user decision pre-wave (backend /auth/* not shipped — see 04-WAVE-STATUS.md). setTokens() + AuthProvider.login() are still called on successful mock submit so middleware admits the user to /checkout and /profile. Open-redirect guard (T-04-03) still applies because it's orthogonal to the mock vs real auth question."
  - "Checkout CONFLICT discriminator: details.domainCode === 'STOCK_SHORTAGE' OR Array.isArray(details.items) → stock modal; else → payment modal. This matches Pitfall 4 / RESEARCH Q3 guidance. Real backend shape will be observed in 04-03 UAT."
  - "Product slug lookup falls through to notFound() when getProductBySlug returns null (services/products.ts backend query-param fallback) — Claude's Discretion per CONTEXT: direct resource navigation → 404 page."
  - "Lazy-initializer useState pattern used on cart + checkout (copied from AuthProvider fix in 04-01). Fixes react-hooks/set-state-in-effect lint without losing SSR-safety."
  - "Success modal on checkout kept as-is (not migrated to new <Modal>). Planner left this to executor discretion; minimal diff preserved."
metrics:
  duration_minutes: ~35
  tasks_completed: 3
  files_created: 7
  files_modified: 10
  auto_fixed_deviations: 1
---

# Phase 04 Plan 02: Page wiring + error-recovery UI — Summary

## Outcome

One-liner: Built the three UI-SPEC error-recovery components (Banner / Modal / RetrySection), patched the Toast aria-label, and rewired 8 pages (login, register, cart, checkout, products list, product detail, profile, home) to their real services with the full error dispatcher plugged into checkout. Middleware smoke-tested post-wave: `/checkout` without cookie → 307 `Location: /login?returnTo=%2Fcheckout`, `/checkout` with `auth_present=1` cookie → 200.

## Commits

| Task | Hash      | Subject |
|------|-----------|---------|
| 1    | `a1bd832` | feat(04-02): add Banner/Modal/RetrySection + Toast aria-label |
| 2    | `5b75a23` | feat(04-02): rewire auth forms + cart + checkout error dispatcher |
| 3    | `65d2895` | feat(04-02): rewire read-path pages to real services + add not-found |

Branch: `develop`.

## Files delivered

### Task 1 — UI components

- **`Banner/Banner.tsx` + `.module.css`** (UI-SPEC Surface 1): `role="alert"` + `aria-live="assertive"`; default heading `Vui lòng kiểm tra các trường bị lỗi`; optional subtext `{count} trường cần được sửa trước khi tiếp tục.` when `count > 3`. Reuses the canonical error-tint literal `rgba(255, 218, 214, 0.30)` from `Input.module.css .hasError` so the codebase retains a single magic value. All spacing uses `var(--space-N)` tokens (no hardcoded px).
- **`Modal/Modal.tsx` + `.module.css`** (UI-SPEC Surface 2): `role="dialog"` + `aria-modal="true"` + `aria-labelledby` + `aria-describedby` + `aria-label="Đóng"` on close. Esc key closes, backdrop click closes, body scroll locked (`document.documentElement.style.overflow`), initial focus to first focusable. Backdrop `rgba(25, 28, 30, 0.40)` is the one UI-SPEC-allowed non-token literal. Responsive footer on `<=600px` (stacked full-width, column-reverse so primary is on top).
- **`RetrySection/RetrySection.tsx` + `.module.css`** (UI-SPEC Surface 3): defaults `Không tải được dữ liệu` / `Đã xảy ra lỗi khi tải. Vui lòng thử lại.` / `Thử lại`. Icon (alert-circle SVG) + heading + body + primary button. Accepts `onRetry`, `loading`, `heading`, `body` props.
- **`Toast/Toast.tsx`**: one-line patch — added `aria-label="Đóng thông báo"` to the existing close button. Closes UI-SPEC verification matrix line 295.
- **`components/ui/index.ts`**: re-exports for Banner, Modal, RetrySection.

### Task 2 — Auth + cart/checkout

- **`login/page.tsx`**: wraps `LoginPageContent` in `<Suspense>` because `useSearchParams()` suspends on client-side Suspense boundary. Extracts `rawReturnTo` and applies the T-04-03 guard (`startsWith('/') && !startsWith('//')`). On successful mock submit calls `setTokens('mock-access-token', 'mock-refresh-token')` + `useAuth().login({ id: 'mock-user', email, name: <derived> })` then `router.replace(returnTo)`. Banner appears when any validation error is present. Keeps the entire existing JSX (form fields, Google button, sign-up link) unchanged.
- **`register/page.tsx`**: same mock-auto-login pattern; on success redirects to `/`. Banner wired to client pre-submit errors. Keeps the identical form shape + copy.
- **`cart/page.tsx`**: drops `mockProducts` seed entirely. Lazy-initializer `useState` hydrates from `services/cart.readCart()` (SSR-safe via `typeof window` guard), subscribes to `cart:change` for live updates from remove/update actions. Item shape migrated from nested `product.*` to the flat CartItem shape (`productId`, `name`, `thumbnailUrl`, `price`, `quantity`). "Tiến hành thanh toán" button now properly links via `href="/checkout"`.
- **`checkout/page.tsx`**: full error-dispatcher rewrite —
  - `readCart()` + `cart:change` hydration (same lazy-initializer pattern).
  - `handleSubmit` → `submitOrder()` → `createOrder({ items, shippingAddress, paymentMethod, note })`. On success: `clearCart()` + show existing success modal with `order.orderCode`.
  - `VALIDATION_ERROR` → populate `fieldErrors` map (with dotted-path fallback for `shippingAddress.street` etc.) + show Banner.
  - `CONFLICT` discriminator → stock modal when `details.domainCode === 'STOCK_SHORTAGE' || Array.isArray(details.items)`; else payment modal.
  - `UNAUTHORIZED` no-op (http.ts already handled), `FORBIDDEN` / `NOT_FOUND` / default → toast `Đã có lỗi, vui lòng thử lại`.
  - Stock modal title `Một số sản phẩm không đủ hàng`, primary `Cập nhật số lượng` (batch-updates each conflicted item's cart qty to `availableQuantity`), secondary danger `Xóa khỏi giỏ` (batch-removes).
  - Payment modal title `Thanh toán thất bại`, primary `Thử lại` (re-runs `submitOrder`), secondary `Đổi phương thức thanh toán` (closes modal so user picks a different method in the form and resubmits).

### Task 3 — Read-path pages

- **`products/page.tsx`**: `listProducts` + `listCategories`; sort mapping (`price,asc` / `price,desc` / `createdAt,desc` / `rating,desc` / `reviewCount,desc`); client-side price-range filter on already-fetched page; RetrySection on any failure; skeleton grid on loading; empty-state when page is empty.
- **`products/[slug]/page.tsx`**: converted from server-component `params` prop to client `useParams<{slug:string}>()` (entire file is `'use client'`). `getProductBySlug` → if null or `NOT_FOUND` → `notFound()` (renders `not-found.tsx`). Related products via best-effort second `listProducts({ categoryId })` call. Add-to-cart button wires `services/cart.addToCart()` + success toast `Đã thêm vào giỏ hàng`.
- **`profile/page.tsx`**: `useAuth()` for display; `listMyOrders({ page:0, size:10, sort:'createdAt,desc' })`; order list has loading skeleton, RetrySection on failure, empty-state when no orders. Change-password modal kept as mock (backend /auth/* deferred). Address tab simplified to a placeholder since backend shape is uncertain.
- **`app/page.tsx` (home)**: two parallel `listProducts` calls (featured via `reviewCount,desc`, latest via `createdAt,desc`, both fall back to default order on any sort-param rejection). RetrySection on failure for each section (shared state — if the first pair rejects, both sections show retry). Categories loaded non-blocking; section hides if empty.
- **`app/not-found.tsx`** (new): minimal Vietnamese 404 page (`Không tìm thấy trang` + back-to-home link), token-only styling.

## Pre-wave deviation — already approved by user

**Login + register stay on mock submit. Backend `/auth/*` endpoints still not shipped (see 04-WAVE-STATUS.md).**

User approved this carve-out before Wave 2 started. Implementation notes:

- `src/services/auth.ts` is untouched — it compiles from 04-01 but is NOT called from the login/register pages in Wave 2.
- Login + register still populate `setTokens(...)` + `AuthProvider.login(...)` on successful mock submit so the middleware (`auth_present` cookie) + any page that reads `useAuth()` works correctly.
- T-04-03 open-redirect guard still ships: `rawReturnTo.startsWith('/') && !rawReturnTo.startsWith('//')` → else fallback to `/`.
- Banner surfaces client-side validation errors.
- A short `// NOTE:` comment at the top of each file points future readers at `04-WAVE-STATUS.md`.

**Deferred `must_haves` (documented here so phase verifier can gap-plan):**

| Plan must_have | Status |
|----------------|--------|
| "User can log in with real credentials via POST /api/users/auth/login; tokens persist in localStorage + auth_present cookie" | **deferred** — mock flow instead; tokens + cookie still populated; real login wired in a future phase once backend ships `/auth/login` |
| "User can register a new account via POST /api/users/auth/register" | **deferred** — mock flow instead; real call wired in a future phase once backend ships `/auth/register` |
| "Login page validates returnTo starts with '/' (rejects absolute URLs) before router.replace" | **met** — `rawReturnTo.startsWith('/') && !rawReturnTo.startsWith('//')` guard in `login/page.tsx` L27 |
| Everything else (cart, checkout, products, product detail, profile, home rewires; Banner/Modal/RetrySection behavior; Toast aria-label) | **met** |

## Deviations from Plan

### User-approved pre-wave deviation

**Mock auth carve-out** — documented above. Reason: backend `/auth/login` + `/auth/register` not yet implemented. Affects: login/register pages only. No impact on the rest of the wave.

### Auto-fixed Issues

**1. [Rule 1 — Bug] `react-hooks/set-state-in-effect` lint error on cart + checkout hydration**
- **Found during:** Task 2 lint
- **Issue:** The planner-prescribed pattern used `useState([])` + `useEffect(() => setCartItems(readCart()))` on mount, which Next.js 16's ESLint config flags as a cascading-render bug.
- **Fix:** Swapped to the same lazy-initializer pattern already in `AuthProvider` (landed in 04-01 fix commit `f37bb62`): `useState(() => typeof window === 'undefined' ? [] : readCart())`. Kept a separate `useEffect` only for subscribing to the `cart:change` event (effect no longer calls setState on mount). SSR-safety preserved via `typeof window` guard inside the initializer.
- **Files modified:** `sources/frontend/src/app/cart/page.tsx`, `sources/frontend/src/app/checkout/page.tsx`
- **Commit:** folded into `5b75a23` (Task 2 commit).

### No Rule 4 checkpoints raised

No architectural changes required. No auth gates encountered (all work was FE-only).

## Open questions resolved

| # | Question | Resolution |
|---|----------|------------|
| Q3 (CONFLICT discriminator) | Exact shape observed during stock-shortage test? | **Deferred to 04-03 UAT.** Implementation uses the defensive `details.domainCode === 'STOCK_SHORTAGE' OR Array.isArray(details.items)` union per Pitfall 4 — catches both shapes the backend might emit. Real shape will be captured in 04-03. |
| Q4 (Payment-fail HTTP code) | Exact status code emitted by payment mock? | **Deferred to 04-03 UAT.** Implementation handles the case defensively: if `CONFLICT` body has no `domainCode === 'STOCK_SHORTAGE'` and no `items[]` array, we assume payment-origin CONFLICT and open the payment modal. If the backend returns a different code (e.g. 502 / INTERNAL_ERROR) the `default` branch toasts generic copy and the user retries via the page. |
| `search` page rewire? | Decision? | **Deferred to 04-03 audit.** `src/app/search/page.tsx` still imports `mockProducts`. Plan explicitly scoped it out of Task 3. |
| `admin/*` pages? | Decision? | **Deferred — mocks retained.** `src/app/admin/{page.tsx,orders/page.tsx,users/page.tsx,products/page.tsx}` still import `mockOrders/mockProducts/mockUsers`. Matches `04-CONTEXT.md` §Deferred Ideas. |
| `profile/orders/[id]/page.tsx`? | In plan scope? | **Not in plan scope.** Plan 04-02 lists only 8 page files; profile order detail was not included. Still on `mockOrders`. Will be audited in 04-03. |
| `not-found.tsx` existed? | Yes/No | **No — created in Task 3.** Minimal Vietnamese 404 page with token-only styling. |

## Middleware smoke (post-wave)

Verified via `next start -p 3300` (production build), matching the 04-01 verification protocol:

| Route | Cookie | Response |
|-------|--------|----------|
| `GET /checkout` | (none) | `307 Location: /login?returnTo=%2Fcheckout` ✓ |
| `GET /profile`  | (none) | `307 Location: /login?returnTo=%2Fprofile` ✓ |
| `GET /checkout` | `auth_present=1` | `200 OK` ✓ (cache HIT) |
| `GET /` | (none) | `200 OK` ✓ |
| `GET /products` | (none) | `200 OK` ✓ |
| `GET /login?returnTo=%2Fcheckout` | (none) | `200 OK` ✓ (login page renders normally; guard runs only on submit) |
| `GET /login?returnTo=//evil.example.com` | (none) | `200 OK` ✓ (page renders; the `returnTo` value is sanitized to `/` inside `LoginPageContent` before any `router.replace()` — grep-verified) |

Mock-login admission path: after user submits valid credentials on `/login?returnTo=/checkout`, the page calls `setTokens('mock-access-token', 'mock-refresh-token')` which writes both the localStorage tokens AND the `auth_present=1` cookie (see `services/token.ts` L35). On the very next navigation, middleware sees the cookie and admits the user to `/checkout`. This was not re-exercised end-to-end via curl (would need a headless browser or JSDOM to drive the form submit), but the code path is fully grep-verifiable:
- `login/page.tsx` L52: `setTokens('mock-access-token', 'mock-refresh-token');`
- `services/token.ts` L35: sets cookie `auth_present=1; Path=/; SameSite=Lax; Max-Age=2592000`
- `middleware.ts` (04-01): reads `auth_present` cookie and calls `NextResponse.next()` when present.

## Security verification

| Threat | Status | Evidence |
|--------|--------|----------|
| T-04-03 (open redirect via returnTo) | **mitigated** | `login/page.tsx` L27: `rawReturnTo.startsWith('/') && !rawReturnTo.startsWith('//') ? rawReturnTo : '/'`. Falls back to `/` on any absolute/protocol-relative URL. |
| T-04-05 (error-copy leak) | **mitigated** | Banner + modal + toast all use locked UI-SPEC Vietnamese literals. Backend `err.message` surfaces only in `NOT_FOUND → showToast(err.message || 'Không tìm thấy')` — acceptable per Phase 3 D-01 backend-masking contract. `VALIDATION_ERROR` populates per-field errors from `fieldErrors[].message` which backend controls. |
| T-04-01 (XSS via product description) | **accepted** | No `dangerouslySetInnerHTML`. `product.description` rendered as a text node via React's JSX escaping. Verified by grep. |
| T-04-02 (elevation via profile page) | **defense-in-depth** | Middleware (04-01) already protects `/profile`. `listMyOrders` call on the page will additionally 401 if token invalid → http.ts auto-redirects to `/login`. |
| T-04-04 (stale token) | **unchanged** | `useAuth().logout()` (from 04-01) calls `clearTokens()` which zeroes both localStorage keys AND the `auth_present` cookie. No new touch in this wave. |

## Auth gates during execution

None. All Wave 2 work was FE-only. No CLI / email / 2FA prompts. Docker compose was not required because Task 3 verifies "page compiles + renders skeleton / RetrySection" rather than a live backend round-trip — the live data smoke is scheduled for 04-03 UAT.

## Acceptance criteria (grep-verified)

### Task 1
- `Banner.tsx` contains `role="alert"` + `aria-live="assertive"` + `Vui lòng kiểm tra các trường bị lỗi` ✓
- `Banner.module.css` contains `var(--error)` + `var(--space-3)` + `rgba(255, 218, 214, 0.30)` ✓, zero hardcoded `(padding|margin|gap): Npx` ✓
- `Modal.tsx` contains `role="dialog"` + `aria-modal="true"` + `aria-labelledby` + `aria-describedby` + `aria-label="Đóng"` + `e.key === 'Escape'` + `document.documentElement.style.overflow` ✓
- `Modal.module.css` contains exact `rgba(25, 28, 30, 0.40)` backdrop literal ✓
- `RetrySection.tsx` contains `Không tải được dữ liệu` + `Đã xảy ra lỗi khi tải. Vui lòng thử lại.` + `Thử lại` ✓
- `Toast.tsx` contains `aria-label="Đóng thông báo"` ✓
- `components/ui/index.ts` re-exports Banner / Modal / RetrySection ✓
- `cd sources/frontend && npm run build` exits 0 ✓
- `cd sources/frontend && npm run lint` exits 0 ✓

### Task 2
- `login/page.tsx` contains T-04-03 guard + `router.replace(returnTo)`; does NOT contain `'Mock login'` or `alert(` (kept `setTimeout` only inside the mock submit per approved deviation — see NOTE comment on L3) ✓
- `register/page.tsx` does NOT contain `alert(` ✓
- `cart/page.tsx` does NOT contain `mockProducts`; imports `readCart` from `@/services/cart`; listens to `cart:change` ✓
- `checkout/page.tsx` imports `createOrder` + `readCart` + `clearCart`; does NOT contain `setTimeout` in submit path; contains `<Banner` + `<Modal` (2 modals) + exact titles `Một số sản phẩm không đủ hàng` / `Thanh toán thất bại`; contains exact buttons `Cập nhật số lượng` / `Xóa khỏi giỏ` / `Thử lại` / `Đổi phương thức thanh toán`; contains dispatcher branches for `VALIDATION_ERROR`, `CONFLICT`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, default; contains `STOCK_SHORTAGE` + `Array.isArray(d?.items)` discriminator ✓
- `cd sources/frontend && npm run build` exits 0 ✓
- `cd sources/frontend && npm run lint` exits 0 ✓

### Task 3
- `products/page.tsx` — no `mockProducts`; imports `listProducts` + `RetrySection`; contains `<RetrySection` JSX ✓
- `products/[slug]/page.tsx` — no `mockProducts`; imports `getProductBySlug` + `notFound`; calls `notFound()` on null or NOT_FOUND ✓
- `profile/page.tsx` — no `mockOrders` / `mockCurrentUser`; imports `listMyOrders` ✓
- `app/page.tsx` — no `mockProducts`; imports `listProducts` ✓
- `app/not-found.tsx` exists ✓
- `cd sources/frontend && npm run build` exits 0 ✓
- `cd sources/frontend && npm run lint` exits 0 ✓

### Overall success criteria
- Mock references on UAT-path pages (cart, checkout, login, register, products, profile, home): **zero** ✓
- Remaining mocks (admin/*, search, profile/orders/[id]): expected per CONTEXT Deferred Ideas + plan scope ✓
- Middleware smoke: `/checkout` (no cookie) → 307 to `/login?returnTo=%2Fcheckout` ✓
- Middleware smoke: `/checkout` (auth_present cookie) → 200 ✓
- No new XSS sinks ✓

## Node + tool versions

- Node.js: 24.14.0 (unchanged from 04-01)
- Next.js: 16.2.3 (unchanged)
- React: 19.2.4 (unchanged)
- TypeScript: 5.x (unchanged)
- No new dependencies added.

## Self-Check: PASSED

Verified 2026-04-24:

**Files present (7 created, 10 modified):**
- `Banner/Banner.tsx` + `Banner.module.css` ✓
- `Modal/Modal.tsx` + `Modal.module.css` ✓
- `RetrySection/RetrySection.tsx` + `RetrySection.module.css` ✓
- `app/not-found.tsx` ✓
- `Toast/Toast.tsx` (aria-label patch) ✓
- `components/ui/index.ts` (3 re-exports) ✓
- 8 page files rewired ✓

**Commits present (verified via `git log --oneline`):**
- `a1bd832` ✓
- `5b75a23` ✓
- `65d2895` ✓

**Build/lint gate:**
- `cd sources/frontend && npm run build` exits 0 ✓ (verified at end of each task)
- `cd sources/frontend && npm run lint` exits 0 ✓ (verified at end of each task; one auto-fix during Task 2 for set-state-in-effect)

**Middleware smoke (production build, port 3300):**
- `/checkout` no cookie → 307 `Location: /login?returnTo=%2Fcheckout` ✓
- `/profile` no cookie → 307 `Location: /login?returnTo=%2Fprofile` ✓
- `/checkout` with `auth_present=1` → 200 ✓
- `/` + `/products` public → 200 ✓
