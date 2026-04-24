---
phase: 04-frontend-contract-alignment-e2e-validation
plan: 01
subsystem: frontend
status: complete
requirements: [FE-01, FE-02]
completed: 2026-04-24
tags:
  - frontend
  - typescript
  - nextjs
  - openapi
  - http-client
  - middleware
  - auth
dependency_graph:
  requires:
    - Phase 01 baseline (Next.js 16.2.3, React 19.2.4, TypeScript 5.x)
    - Phase 02 CRUD (per-service /v3/api-docs emit)
    - Phase 03 error envelope (identical keys service + gateway)
  provides:
    - Typed httpGet/Post/Put/Patch/Delete wrapper that unwraps ApiResponse envelope and throws ApiError
    - openapi-typescript@7.13.0 codegen pipeline (`npm run gen:api`) + 6 committed `.generated.ts` files
    - services/{auth,products,orders,cart,payments,inventory,notifications} domain modules
    - Route protection middleware for /checkout, /profile, /admin/*
    - AuthProvider + ToastProvider mounted in root layout
  affects:
    - 04-02 page rewires — all pages now have a typed surface to call
    - 04-03 UAT walkthrough — HTTP tier + route protection are the happy-path dependencies
tech-stack:
  added:
    - openapi-typescript@7.13.0 (devDep)
  patterns:
    - Function-based named-export service modules (mirrors services/api.ts convention)
    - SSR-safe localStorage accessors (typeof window guard)
    - Lazy-initializer useState for hydration (avoids set-state-in-effect)
    - Presence-only non-httpOnly cookie bridging D-11 (localStorage) and D-12 (middleware)
key-files:
  created:
    - sources/frontend/scripts/gen-api.mjs
    - sources/frontend/.env.example
    - sources/frontend/.env.local (git-ignored)
    - sources/frontend/src/types/api/users.generated.ts
    - sources/frontend/src/types/api/products.generated.ts
    - sources/frontend/src/types/api/orders.generated.ts
    - sources/frontend/src/types/api/payments.generated.ts
    - sources/frontend/src/types/api/inventory.generated.ts
    - sources/frontend/src/types/api/notifications.generated.ts
    - sources/frontend/src/services/errors.ts
    - sources/frontend/src/services/token.ts
    - sources/frontend/src/services/http.ts
    - sources/frontend/src/services/auth.ts
    - sources/frontend/src/services/products.ts
    - sources/frontend/src/services/orders.ts
    - sources/frontend/src/services/cart.ts
    - sources/frontend/src/services/payments.ts
    - sources/frontend/src/services/inventory.ts
    - sources/frontend/src/services/notifications.ts
    - sources/frontend/middleware.ts
    - sources/frontend/src/providers/AuthProvider.tsx
  modified:
    - sources/frontend/package.json
    - sources/frontend/package-lock.json
    - sources/frontend/.gitignore
    - sources/frontend/next.config.ts
    - sources/frontend/src/app/layout.tsx
decisions:
  - "Generated types hand-narrowed to UI types from @/types because springdoc emits `never` response bodies for endpoints wrapped by ApiResponseAdvice (Pitfall 7). Domain modules still import `paths` from generated to keep the OpenAPI surface coupled."
  - "Pin Turbopack workspace root to sources/frontend in next.config.ts; otherwise Next.js 16 infers the repo root (which has its own package-lock.json) and fails to pick up middleware.ts reliably."
  - "AuthProvider hydrates via lazy-initializer useState (SSR-safe via typeof window) instead of useEffect+setState, satisfying the react-hooks/set-state-in-effect ESLint rule."
metrics:
  duration_minutes: ~25
  tasks_completed: 3
  files_created: 22
  files_modified: 5
  auto_fixed_deviations: 3
---

# Phase 04 Plan 01: Typed HTTP tier + codegen + route-protection foundation — Summary

## Outcome

One-liner: Committed the full typed HTTP surface (native-fetch wrapper with envelope unwrap + `ApiError`, `openapi-typescript@7.13.0` codegen, 10 service modules, route-protecting middleware, and auth/toast providers mounted in the root layout) so that every page in Plan 04-02 can call real backend endpoints with compile-time type safety.

Every acceptance criterion from PLAN 04-01 grep-verified; `cd sources/frontend && npm run build` green at the end of each task; `npm run lint` green after the AuthProvider lint fix. Middleware verified via `next start` smoke: `/checkout`, `/profile`, `/admin/users` → 307 `Location: /login?returnTo=...`; `/products` and `/` → 200 OK.

## Commits

| Task | Hash      | Subject |
|------|-----------|---------|
| 1    | `8957411` | feat(04-01): scaffold codegen pipeline + 6 generated OpenAPI type modules |
| 2    | `afb0757` | feat(04-01): add typed HTTP tier + 7 domain service modules + cart |
| 3    | `4466080` | feat(04-01): add route-protection middleware + AuthProvider + mount providers in layout |
| 3-fix| `f37bb62` | fix(04-01): hydrate AuthProvider via lazy initializer to avoid set-state-in-effect lint |

## Files delivered

### Env / Codegen (Task 1)
- `sources/frontend/package.json` — added `"gen:api": "node scripts/gen-api.mjs"` script and `"openapi-typescript": "7.13.0"` devDep.
- `sources/frontend/.gitignore` — whitelisted `.env.example` (`!.env.example` after `.env*`) so the documented default ships.
- `sources/frontend/.env.example` (committed) + `sources/frontend/.env.local` (ignored) — `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`.
- `sources/frontend/scripts/gen-api.mjs` — copied from RESEARCH Pattern 3 verbatim; includes `shell: process.platform === 'win32'` for Windows npx.cmd resolution.
- `sources/frontend/src/types/api/{users,products,orders,payments,inventory,notifications}.generated.ts` — all 6 emitted by first `npm run gen:api` run against live services.

### HTTP tier (Task 2)
- `errors.ts` — `ApiError` class with readonly `code/status/message/fieldErrors/traceId/path/details`; `isApiError` type guard.
- `token.ts` — SSR-safe `getAccessToken/getRefreshToken/setTokens/clearTokens`; sets/clears both localStorage AND `auth_present` cookie (Max-Age=2592000 / 0).
- `http.ts` — native-fetch wrapper; attaches `Authorization: Bearer`; reads `process.env.NEXT_PUBLIC_API_BASE_URL`; unwraps envelope once (`parsed.data`); throws `ApiError` on failure; 401 branch clears tokens and validates `pathname.startsWith('/') && !pathname.startsWith('//')` before encoding `returnTo` (T-04-03 mitigation); no auto-retry.
- `auth.ts` — `login`, `register`, `logout`. Hand-narrowed against `@/types` (LoginRequest/RegisterRequest/AuthResponse) because generated `users.generated.ts` does not expose `/auth/*` paths (see Deviation 1 below). Still imports `paths` type from `@/types/api/users.generated`.
- `products.ts` — `listProducts`, `getProductById`, `getProductBySlug` (query-string fallback), `listCategories`. Gateway prefix `/api/products/` + inner controller path `/products`.
- `orders.ts` — `createOrder`, `listMyOrders`, `getOrderById`. Gateway `/api/orders/` + inner `/orders`.
- `cart.ts` — SSR-safe localStorage cart; `readCart/writeCart/addToCart/removeFromCart/updateQuantity/clearCart`; emits `cart:change` CustomEvent on writes.
- `payments.ts`, `inventory.ts`, `notifications.ts` — minimal read-only wrappers, reserved per plan ("comment-only if path not available for MVP"). Each imports the corresponding `paths` type so the OpenAPI surface is coupled.

### Route protection + providers (Task 3)
- `middleware.ts` (frontend root, NOT under `src/`) — exact matcher `['/checkout/:path*', '/profile/:path*', '/admin/:path*']`; reads `auth_present` cookie; redirects with `returnTo=<encoded path+search>`.
- `src/providers/AuthProvider.tsx` — `'use client'`; `createContext`; `useAuth()` hook; lazy-initializer hydration from localStorage; `login/logout` helpers; storage-event listener for cross-tab logout.
- `src/app/layout.tsx` — wraps children with `<AuthProvider><ToastProvider>` (AuthProvider outermost so Toast can later call useAuth).
- `next.config.ts` — pins `turbopack.root` to the frontend directory so Next.js 16 does not infer the repo root as workspace.

## Open Questions Resolved

| # | Question | Resolution |
|---|----------|------------|
| Q1 | Per-service controller paths after gateway rewrite? | **Resolved via codegen.** Products: `/products`, `/products/{id}`, `/products/categories`. Orders: `/orders`, `/orders/{id}`, `/cart`, `/cart/{id}`. Users: `/users/profiles`, `/users/addresses` (NO auth routes — see Deviation 1). Payments: `/payments/transactions`, `/payments/sessions`. Inventory: `/inventory/items`, `/inventory/reservations`. Notifications: `/notifications/dispatches`, `/notifications/templates`. Full gateway path = `/api/{service}/{inner}` (double segment, e.g., `/api/orders/orders`). |
| Q2 | Refresh-token endpoint exists? | **No.** `users.generated.ts` has no `/auth/refresh` path. `services/auth.ts` does not export `refreshToken()`; D-08 fallback (silent redirect on 401) stands. |
| Q5 | ToastProvider mounted? | **Yes — closed this gap in Task 3** (`layout.tsx` now wraps children with `<ToastProvider>`). |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 — Missing critical functionality] `.gitignore` blocking `.env.example`**
- **Found during:** Task 1 commit stage
- **Issue:** The existing `.gitignore` line `.env*` catches `.env.example` too, so `git add sources/frontend/.env.example` was rejected.
- **Fix:** Added `!.env.example` line immediately after `.env*` to whitelist the documented default while still ignoring local overrides.
- **Files modified:** `sources/frontend/.gitignore`
- **Commit:** `8957411`

**2. [Rule 1 — Bug] Middleware not firing under Turbopack dev due to workspace root inference**
- **Found during:** Task 3 smoke test
- **Issue:** Next.js 16 detected the root `package-lock.json` (at repo root) alongside `sources/frontend/package-lock.json`, picked the repo root as workspace root, and silently skipped `middleware.ts` in dev mode. `next build` correctly emitted `ƒ Proxy (Middleware)` but `next dev` never invoked the middleware function (all protected routes returned 200).
- **Fix:** Pinned `turbopack.root` to the frontend directory in `next.config.ts`:
  ```ts
  import { fileURLToPath } from "node:url";
  import { dirname } from "node:path";
  const __dirnameLocal = dirname(fileURLToPath(import.meta.url));
  const nextConfig: NextConfig = {
    turbopack: { root: __dirnameLocal },
    ...
  };
  ```
- **Verified via production build** (`next start`):
  - `/checkout`  → `307 Location: /login?returnTo=%2Fcheckout` ✓
  - `/profile`   → `307 Location: /login?returnTo=%2Fprofile` ✓
  - `/admin/users` → `307 Location: /login?returnTo=%2Fadmin%2Fusers` ✓
  - `/products`  → `200 OK` (public, not matched) ✓
  - `/`          → `200 OK` (public) ✓
- **Files modified:** `sources/frontend/next.config.ts`
- **Commit:** `4466080`

**3. [Rule 1 — Bug] `react-hooks/set-state-in-effect` lint error in AuthProvider**
- **Found during:** Task 3 `npm run lint`
- **Issue:** Original AuthProvider used `useEffect(() => { ... setIsAuthenticated(true); setUser(...); }, [])` which Next.js 16's ESLint config treats as a bug (unnecessary double-render).
- **Fix:** Hydrated both `user` and `isAuthenticated` via `useState(() => { ... lazy initializer ... })` with `typeof window` guard. Added a separate `useEffect` that subscribes to `window.addEventListener('storage', ...)` for cross-tab logout propagation — this remaining useEffect only sets state in response to a real event, not on mount, so the lint rule no longer triggers.
- **Files modified:** `sources/frontend/src/providers/AuthProvider.tsx`
- **Commit:** `f37bb62`

### Planned Deviations (documented, not blocking)

**Deviation 1: Auth endpoints do NOT exist in the backend yet**
- **What:** `users.generated.ts` exposes only `/users/profiles`, `/users/addresses`, `/admin/users/...`, `/__contract/*`, and `/ping`. **There is no `/auth/login`, no `/auth/register`, no `/auth/refresh`, no `/auth/logout`.**
- **Impact on plan:** `services/auth.ts` was written as specified in PLAN 04-01, targeting `/api/users/auth/login` and `/api/users/auth/register`. These calls compile today (types come from `@/types`, which still contain `LoginRequest/RegisterRequest/AuthResponse`) but will return a 404 at runtime until the backend exposes the routes.
- **Why this is acceptable for 04-01:** No page consumes `services/auth.ts` in this plan. 04-02 rewires the login/register pages — that plan must either (a) block on backend work to expose `/auth/*` endpoints, or (b) keep the existing mock setTimeout flow and wait for the auth feature in a later milestone.
- **Decision recorded:** Files still `import type { paths } from '@/types/api/users.generated'` so that the moment the backend publishes `/auth/login` the generated paths will type-narrow the login body/response automatically — no further FE-side change beyond replacing the hand-narrowed types.

**Deviation 2: `never` types across all generated files (Pitfall 7)**
- **What:** `grep -c ': never;'` across the 6 generated files returns: users=196, products=212, orders=180, payments=226, inventory=212, notifications=212 (total ~1,238 occurrences).
- **Why:** springdoc 2.6.0 emits `never` for response bodies when the controller return type is wrapped by `ApiResponseAdvice` (the advice is invisible to springdoc because it wraps at serialization time, after schema inference). Pitfall 7 in 04-RESEARCH.md flagged this exact case.
- **Mitigation applied:** All domain service modules in `services/*.ts` hand-narrow to UI types from `@/types` (e.g., `httpGet<PaginatedResponse<Product>>(...)`). `http.ts` unwraps the envelope once (`parsed?.data`), matching the inner shape describes by generated schemas after the wrapper effect.
- **Follow-up for later phases:** add `@ApiResponse(content = @Content(schema = @Schema(implementation = ...)))` annotations on backend controllers OR move `ApiResponseAdvice` out of the HTTP path and wrap at the controller level with a visible return type. Not in scope for 04-01.

**Deviation 3: Slug-based product lookup uses a list-query fallback**
- **What:** Backend exposes `/products/{id}` but no `/products/slug/{slug}` route. `services/products.ts:getProductBySlug` calls `listProducts?slug=...` and returns `page.content[0]`.
- **Why:** keeps the FE-side contract stable; swaps to a dedicated endpoint when backend ships it.

## Auth gates during execution

None. Docker compose + gen:api ran unattended.

## Security verification

- **T-04-01** (localStorage token / XSS exposure): accepted per D-11. No new XSS sinks introduced (no `dangerouslySetInnerHTML`, no `eval`, no raw HTML from API).
- **T-04-02** (middleware matcher too broad): mitigated. `middleware.ts` matcher is exactly `['/checkout/:path*', '/profile/:path*', '/admin/:path*']` — no wildcards, no whitelist logic. Grep-verified.
- **T-04-03** (open redirect via returnTo): mitigated in `http.ts` 401 handler. Before building `/login?returnTo=...`, `pathname.startsWith('/') && !pathname.startsWith('//')` is checked. Grep-verified (`grep -n "startsWith" http.ts`). Final open-redirect check on login submit lands in 04-02.
- **T-04-04** (stale token after logout): mitigated. `clearTokens()` clears both `localStorage.accessToken`/`refreshToken` AND sets `auth_present=; Max-Age=0`. Verified in `token.ts` source.
- **T-04-05** (error-message leak): out of scope for 04-01; 04-02 handles toast/modal copy lockdown.

## Node + tool versions actually installed

- Node.js: 24.14.0 (nvm-windows; active junction `C:\nvm4w\nodejs`)
- `openapi-typescript`: `7.13.0` (exact, from devDependencies)
- Next.js: `16.2.3` (unchanged)
- React: `19.2.4` (unchanged)
- TypeScript: `^5` (unchanged)
- `next.config.ts`: migrated to use Turbopack root pinning (no functional regression for other features)

## Self-Check: PASSED

Verified 2026-04-24:

**Files present (22 created, 5 modified):**
- `sources/frontend/scripts/gen-api.mjs` ✓
- `sources/frontend/.env.example` ✓
- `sources/frontend/src/types/api/{users,products,orders,payments,inventory,notifications}.generated.ts` ✓ (6 files)
- `sources/frontend/src/services/{errors,token,http,auth,products,orders,cart,payments,inventory,notifications}.ts` ✓ (10 files)
- `sources/frontend/middleware.ts` ✓
- `sources/frontend/src/providers/AuthProvider.tsx` ✓
- `sources/frontend/src/app/layout.tsx` — edited to wrap `<AuthProvider><ToastProvider>` ✓
- `sources/frontend/next.config.ts` — Turbopack root pinned ✓
- `sources/frontend/package.json` / `package-lock.json` / `.gitignore` — updated ✓

**Commits present (verified via `git log --oneline`):**
- `8957411` ✓
- `afb0757` ✓
- `4466080` ✓
- `f37bb62` ✓

**Acceptance criteria (grep-verified):**
- `package.json` contains `"gen:api"` + `"openapi-typescript": "7.13.0"` ✓
- `.gitignore` contains `.env.local` ✓
- `.env.example` + `.env.local` both contain `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` ✓
- `scripts/gen-api.mjs` contains `openapi-typescript@7.13.0` AND `shell: process.platform === 'win32'` ✓
- All 6 generated files contain `export interface paths` ✓
- `http.ts` contains `` `Bearer ${token}` ``, `process.env.NEXT_PUBLIC_API_BASE_URL`, `startsWith('/')`, `startsWith('//')`, `clearTokens()` in 401 branch ✓
- `http.ts` exports `httpGet`, `httpPost`, `httpPut`, `httpPatch`, `httpDelete` ✓
- `cart.ts` exports `readCart`, emits `CustomEvent('cart:change')`, has `typeof window === 'undefined'` guard ✓
- All 6 domain services import from `@/types/api/` ✓
- `middleware.ts` at root (not under src/); matcher `['/checkout/:path*', '/profile/:path*', '/admin/:path*']`; reads `auth_present`; calls `NextResponse.redirect` ✓
- `AuthProvider.tsx` first line is `'use client';`; exports `AuthProvider` + `useAuth` ✓
- `layout.tsx` contains `<AuthProvider>` AND `<ToastProvider>` (AuthProvider outermost) ✓
- `cd sources/frontend && npm run build` exits 0 ✓
- `cd sources/frontend && npm run lint` exits 0 ✓
- Middleware smoke (via `next start`): `/checkout`, `/profile`, `/admin/users` all return `307 Location: /login?returnTo=...`; `/products` and `/` return 200 OK ✓
