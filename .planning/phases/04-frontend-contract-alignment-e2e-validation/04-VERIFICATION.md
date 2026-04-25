---
phase: 04-frontend-contract-alignment-e2e-validation
verified: 2026-04-25T00:00:00Z
status: gaps_found
score: 2/3 success criteria verified (SC#3 met; SC#1 + SC#2 fail at runtime)
overrides_applied: 2
overrides:
  - must_have: "User can log in with real credentials via POST /api/users/auth/login; tokens persist in localStorage + auth_present cookie"
    reason: "Backend /auth/* endpoints not yet implemented (carry-over blocker from Wave 1). User-approved pre-wave deviation in 04-02; mock submit still populates setTokens + AuthProvider.login + auth_present cookie so middleware admit path works. T-04-03 returnTo guard still ships. Documented in 04-02-SUMMARY §Pre-wave deviation and 04-WAVE-STATUS.md. Real call deferred to a future phase when backend ships /auth/login."
    accepted_by: "dminhduc25013615"
    accepted_at: "2026-04-24T00:00:00Z"
  - must_have: "User can register a new account via POST /api/users/auth/register"
    reason: "Same carve-out — backend /auth/register not yet implemented. Mock submit path still hardens tokens + cookie + AuthProvider state. Documented in 04-02-SUMMARY §Pre-wave deviation."
    accepted_by: "dminhduc25013615"
    accepted_at: "2026-04-24T00:00:00Z"
re_verification:
  previous_status: none
  previous_score: n/a
  gaps_closed: []
  gaps_remaining: []
  regressions: []
gaps:
  - truth: "Frontend uses the documented endpoints/DTOs and handles standardized errors without breaking UX (Phase 4 SC #1)"
    status: failed
    reason: "Compile-time achieved (codegen + httpGet/Post + 6 generated.ts files compile clean); runtime NOT aligned. UAT walked the real backend and three concrete contract mismatches surfaced — pages crash before SC#1 can be observed end-to-end."
    artifacts:
      - path: "sources/frontend/src/components/ui/ProductCard/ProductCard.tsx"
        issue: "Lines 35, 72, 95, 103 access product.thumbnailUrl, product.category.name, Math.floor(product.rating), product.reviewCount with no null-guard. Backend returns thin DTO {id, name, slug, categoryId, price, status, ...} — category, rating, reviewCount, thumbnailUrl, tags, shortDescription all undefined → TypeError unmounts the React tree on first render. Cascades to home (A1), products list (A3), product detail's related-products grid (A4 prerequisite)."
      - path: "sources/frontend/src/services/orders.ts (createOrder body) + sources/frontend/src/app/checkout/page.tsx (handleSubmit)"
        issue: "FE sends domain-command body {items, shippingAddress, paymentMethod, note}; backend OrderUpsertRequest requires userId (should be JWT-derived) + status (should default PENDING server-side). Every checkout submit returns 400 VALIDATION_ERROR with fieldErrors=[{field:userId},{field:status}]. UAT row A5 FAIL."
      - path: "backend product-service /api/products/products/slug/{slug}"
        issue: "Returns HTTP 500 INTERNAL_ERROR with generic body (FE side proxies through services/products.ts:getProductBySlug fallback to listProducts?slug= per 04-01 Deviation 3 — but the dedicated slug endpoint that exists also 500s on direct-link navigation). Detail page deep-link can't load. Root cause masked by Phase 3 GlobalExceptionHandler.handleFallback discarding Throwable — see .planning/debug/products-list-500.md."
    missing:
      - "Backend product-service: enrich Product DTO — serialize Category join (category.name, category.slug); add fields thumbnailUrl, description, shortDescription, tags, rating, reviewCount with sane defaults if absent in DB. Match the rich type in sources/frontend/src/types/index.ts that ProductCard already consumes."
      - "Backend order-service: replace OrderUpsertRequest with CreateOrderCommand accepting {items[], shippingAddress, paymentMethod, note}; derive userId from JWT (or session header for now); set status=PENDING server-side. Update OpenAPI emit so codegen surfaces the new shape."
      - "Backend product-service: fix /products/slug/{slug} 500. Optionally fold in the GlobalExceptionHandler observability fix from .planning/debug/products-list-500.md (Option A — log original Throwable instead of discarding)."
      - "Frontend ProductCard: add null-guards for thumbnailUrl/category.name/rating/reviewCount even after backend ships rich DTO (defense-in-depth, also addresses code-review WR-04)."
  - truth: "Shopping flow validated end-to-end against running backend: browse → cart → checkout → payment (mock) → confirmation (Phase 4 SC #2)"
    status: failed
    reason: "Cascade from SC#1 gap. UAT 04-UAT.md rows A1, A3, A4, A5 all FAIL. The browse step (A1, A3) crashes during initial product render; the cart step (A4) cannot reach the Add-to-Cart button because /products/slug/{slug} returns 500; the checkout step (A5) is rejected by backend with 400 VALIDATION_ERROR on every submit. The only happy-path rows that PASS are A2 (mock register fallback — covered by override) and A6 (profile + listMyOrders — empty list because A5 didn't persist)."
    artifacts:
      - path: "04-UAT.md rows A1, A3, A4, A5"
        issue: "All FAIL. Root causes documented in row Notes columns. Screenshots in sources/frontend/e2e/screenshots/A1.png, A3.png, A4.png, A5.png provide visual evidence."
    missing:
      - "Closure of SC#1 gaps (above) is necessary AND sufficient for SC#2 to pass — the same Phase 4.1 backend changes unblock A1, A3, A4, A5 simultaneously."
      - "Phase 4.1-03: re-execute Playwright suite (sources/frontend/e2e/uat.spec.ts) end-to-end with NO stubs for B2/B3 (resolves Q3 + Q4) and NO stubs for B4a/B5 (verify real-backend 401 + 5xx behavior). Expected outcome: 12/12 PASS."
deferred:
  - truth: "Real CONFLICT stock-shortage shape — does backend emit details.domainCode === 'STOCK_SHORTAGE' or Array.isArray(details.items)?"
    addressed_in: "Phase 4.1 (recommended plan 04.1-02 — wire order-service.createOrder → inventory-service.reserve)"
    evidence: "04-03-SUMMARY §Open questions UNRESOLVED Q3; 04-UAT.md row B2 Notes — currently STUBBED via Playwright page.route(). FE dispatcher verified against designed shape only."
  - truth: "Real payment-failure HTTP code + body — does backend emit 409 / 402 / 502?"
    addressed_in: "Phase 4.1 (recommended plan 04.1-02 / optionally 04.1-04 — integrate payment-service into checkout chain)"
    evidence: "04-03-SUMMARY §Open questions UNRESOLVED Q4; 04-UAT.md row B3 Notes — currently STUBBED via Playwright page.route(). FE dispatcher verified against designed shape only."
human_verification: []
---

# Phase 04: Frontend Contract Alignment + E2E Validation — Verification Report

**Phase Goal (from ROADMAP.md):** "Ensure the Next.js frontend is aligned to the backend contracts and the key flows behave reliably."

**Verified:** 2026-04-25
**Status:** gaps_found
**Re-verification:** No — initial verification

---

## Goal Achievement

### Roadmap Success Criteria (must always verify — non-negotiable)

| # | Success Criterion | Status | Evidence |
|---|-------------------|--------|----------|
| 1 | Frontend uses the documented endpoints/DTOs and handles standardized errors without breaking UX. | FAIL | Compile-time aligned (codegen + 10 service modules + httpGet/Post all compile clean); runtime NOT aligned — UAT 04-UAT.md A1/A3/A4/A5 all FAIL. ProductCard crashes on undefined `category.name`/`rating`/`reviewCount`/`thumbnailUrl`; checkout submit gets 400 every time because backend OrderUpsertRequest requires userId+status; /products/slug/{slug} returns 500. See gaps[] entries. |
| 2 | Shopping flow validated end-to-end locally: browse → cart → checkout → payment (mock) → confirmation. | FAIL | Cascade from SC#1. A1 (home crash), A3 (products crash), A4 (slug 500 + cascade), A5 (order DTO mismatch) all FAIL in UAT. The flow cannot be walked end-to-end against the real backend. Only A2 (override — mock register) and A6 (profile, empty order list) PASS. |
| 3 | Failures during checkout (validation, stock, payment) are surfaced clearly and recoverably in the UI. | PASS | All 5 dispatcher branches verified in UAT: B1 real-backend 400 → Banner "Vui lòng kiểm tra các trường bị lỗi" ✓; B2 stubbed CONFLICT/STOCK_SHORTAGE → Stock modal "Một số sản phẩm không đủ hàng" + Cập nhật/Xóa khỏi giỏ ✓; B3 stubbed CONFLICT/PAYMENT → Payment modal "Thanh toán thất bại" + Thử lại/Đổi phương thức ✓; B4a stubbed 401 → silent redirect + clearTokens (T-04-04) ✓; B4b real returnTo=evil.example.com → land on / (T-04-03) ✓; B5 stubbed 502 → Toast + exactly 1 POST attempt (D-10) ✓. FE-02 marked `met` in REQUIREMENTS.md. |

**Roadmap SC score:** 1/3 met (SC#3); 2/3 fail at runtime (SC#1, SC#2).

### Plan-Level Observable Truths (with override resolution)

The 04-01 / 04-02 / 04-03 PLAN frontmatters expose 25+ truths in total. Below is the consolidated, deduplicated list mapped to UAT evidence.

| # | Truth (source plan) | Status | Evidence |
|---|---------------------|--------|----------|
| 1 | `npm run gen:api` produces 6 committed src/types/api/*.generated.ts files that compile (04-01) | PASS | `ls sources/frontend/src/types/api/` lists all 6 files. 04-01-SUMMARY confirms `cd sources/frontend && npm run build` exits 0. |
| 2 | services/http.ts attaches Authorization: Bearer, unwraps envelope, throws ApiError on failure (04-01) | PASS | Read sources/frontend/src/services/http.ts: line 46 `headers['Authorization'] = \`Bearer ${token}\``; line 67 `(parsed as ApiEnvelope<T> \| null)?.data as T`; line 89 `throw new ApiError(...)`. |
| 3 | On 401, services/http.ts clears tokens and redirects to /login?returnTo=<path> (path-only) (04-01) | PASS | http.ts lines 74-86: `clearTokens()` then `pathname.startsWith('/') && !pathname.startsWith('//')` guard before encoding. T-04-03 + T-04-04 mitigations grep-verified. |
| 4 | Navigating to /checkout, /profile, /admin/* without auth_present cookie redirects to /login?returnTo=<path> (04-01) | PASS | sources/frontend/middleware.ts lines 21-28; matcher ['/checkout/:path*', '/profile/:path*', '/admin/:path*']. 04-01 + 04-02 production-build smoke confirms 307 Location: /login?returnTo=... for all three; / and /products return 200. |
| 5 | ToastProvider and AuthProvider mounted in app/layout.tsx (04-01) | PASS | 04-01-SUMMARY commit 4466080 + f37bb62; layout.tsx wraps `<AuthProvider><ToastProvider>` with AuthProvider outermost. |
| 6 | services/cart.ts reads/writes localStorage['cart'] with CartItem[] and emits cart:change events (04-01) | PASS | 04-01-SUMMARY confirms readCart/writeCart/addToCart/removeFromCart/updateQuantity/clearCart all SSR-safe via `typeof window` guard, write+dispatch CustomEvent('cart:change'). |
| 7 | npm run build is green after all service modules compile against generated types (04-01) | PASS | 04-01-SUMMARY + 04-02-SUMMARY: build + lint exit 0 at end of every task. |
| 8 | User can log in with real credentials via POST /api/users/auth/login; tokens persist (04-02) | PASSED (override) | Override accepted by dminhduc25013615 on 2026-04-24 — backend /auth/login not yet implemented. Mock submit still populates setTokens + AuthProvider.login + auth_present cookie so middleware admit works. UAT A2 PASS via mock fallback. |
| 9 | User can register a new account via POST /api/users/auth/register (04-02) | PASSED (override) | Same carve-out — see truth 8. UAT A2 PASS via mock fallback. |
| 10 | Cart page reads items from localStorage via services/cart.readCart() — no mockProducts hardcode (04-02) | PASS | 04-02-SUMMARY: grep `mockProducts` against sources/frontend/src/app/cart/page.tsx returns nothing; cart hydrates via lazy-initializer `useState(() => typeof window === 'undefined' ? [] : readCart())`. |
| 11 | Checkout submit POSTs a full CreateOrderRequest to /api/orders via services/orders.createOrder (04-02) | PARTIAL | Read sources/frontend/src/app/checkout/page.tsx: imports `createOrder`, calls it inside `handleSubmit`. The CALL happens; but the backend rejects every payload because the FE-side DTO doesn't include userId+status. Wiring is correct; contract mismatch is what fails. Counts as ARTIFACT/WIRING PASS, OUTCOME FAIL — rolled into SC#1 gap above. |
| 12 | On VALIDATION_ERROR, top banner appears + each field shows inline error from fieldErrors[] (04-02) | PASS | UAT B1 walked the real backend; Banner with exact text "Vui lòng kiểm tra các trường bị lỗi" visible; fieldErrors mapped onto the form via setFieldErrors. (Field-error mapping went to userId/status not the address fields because backend complains about wrong fields — that's the FE-01 gap, not the FE-02 dispatcher.) |
| 13 | On CONFLICT with stock-shortage, stock modal opens with Cập nhật số lượng / Xóa khỏi giỏ (04-02) | PASS (dispatcher) | UAT B2 PASS via Playwright page.route() stub returning details.domainCode='STOCK_SHORTAGE'. FE dispatcher verified against the designed shape; real backend shape (Q3) deferred to Phase 4.1. |
| 14 | On CONFLICT during payment (or payment mock failure), payment-fail modal opens with Thử lại / Đổi phương thức thanh toán (04-02) | PASS (dispatcher) | UAT B3 PASS via Playwright page.route() stub returning details.domainCode='PAYMENT_FAILED'. Real backend shape (Q4) deferred to Phase 4.1. |
| 15 | On INTERNAL_ERROR / 5xx / network failure, toast "Đã có lỗi, vui lòng thử lại" + RetrySection (no auto-retry on POST/PUT/DELETE) (04-02) | PASS | UAT B5 PASS via stubbed 502; toast visible; POST attempts counted = 1 (D-10 verified). |
| 16 | Products page, product detail, profile, home load from real services — no mockProducts (04-02) | PASS (wiring) / FAIL (runtime) | grep `mockProducts` on these 4 files returns NOTHING (mock-data audit clean). Real services are wired. But A1 (home), A3 (products), A4 (slug detail) all crash at runtime due to FE-01 DTO gap. Wiring is correct; outcome rolled into SC#1 gap. |
| 17 | Login page validates returnTo starts with '/' (rejects absolute URLs) (04-02) | PASS | sources/frontend/src/app/login/page.tsx L26-27: `rawReturnTo.startsWith('/') && !rawReturnTo.startsWith('//') ? rawReturnTo : '/'`. UAT B4b walked it for real — final URL = http://localhost:3000/ despite returnTo=https://evil.example.com/. T-04-03 verified end-to-end. |
| 18 | Toast close button has aria-label="Đóng thông báo" (04-02) | PASS | 04-02-SUMMARY grep-verified; UI-SPEC verification matrix line 295 closed. |
| 19 | 04-UAT.md exists with rows A1..A6 + B1..B5 fully filled with observations (04-03) | PASS | File exists; 12 rows (B4 split into B4a + B4b — documented deviation); all rows have non-empty Actual Observation + Pass/Fail. Frontmatter status: complete. |
| 20 | Happy-path walkthrough executed end-to-end (04-03) | PARTIAL | Walkthrough executed via Playwright headless hybrid 80/20; 7/12 PASS, 5/12 FAIL documented. The walkthrough WAS executed — but the path could not complete end-to-end due to FE-01 gaps. Rolled into SC#2. |
| 21 | All five failure cases executed (04-03) | PASS | B1 real, B2/B3/B4a/B5 stubbed (with documented reasons), B4b real. All 5 PASS. |
| 22 | No UAT-path page imports from @/mock-data (04-03) | PASS | grep audit: cart/checkout/login/register/products/products/[slug]/profile/page.tsx all clean. Residual mocks confined to admin/*, search/, profile/orders/[id]/ per CONTEXT §Deferred Ideas. |
| 23 | sources/frontend/README.md documents gen:api prerequisite + .env.local + middleware (04-03) | PASS | 04-03-SUMMARY commit c6c32d3 added §Phase 4 Dev Workflow with gen:api + NEXT_PUBLIC_API_BASE_URL + auth_present + docker compose sections. |
| 24 | sources/frontend/src/types/index.ts retains UI-owned types (04-03) | PASS | Best-effort cleanup retained per 04-03-SUMMARY. ProductFilter, PaginatedResponse, CartItem still exported. Backend-derived DTO duplicates retained with note (Phase 5 cleanup candidate). |
| 25 | mock-data/ disposition decided + documented in SUMMARY (04-03) | PASS | 04-03-SUMMARY records decisions: search → TODO marker (Phase 5); api.ts → LEGACY comment (admin still consumes); admin/* + profile/orders/[id]/ retained per CONTEXT Deferred Ideas. |

**Plan-truth score:** 22 PASS / 2 PASSED-via-override / 1 PARTIAL (rolled into SC#1 gap). Pure wiring is solid. Outcome failures all trace to backend contract mismatches, not FE wiring defects.

---

### Required Artifacts (sample — full list verified via 04-01/02/03 SUMMARYs)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `sources/frontend/scripts/gen-api.mjs` | Codegen runner with openapi-typescript@7.13.0 + Windows-shell flag | VERIFIED | File exists; 04-01-SUMMARY grep-verified. |
| `sources/frontend/src/types/api/{6 services}.generated.ts` | All 6 OpenAPI-emitted type modules committed | VERIFIED | Listed via `ls`: users, products, orders, payments, inventory, notifications all present. |
| `sources/frontend/src/services/{10 modules}.ts` | errors, token, http, auth, products, orders, cart, payments, inventory, notifications | VERIFIED | All 10 listed in `ls sources/frontend/src/services/`. (api.ts retained as LEGACY per 04-03 decision.) |
| `sources/frontend/middleware.ts` | Root-level matcher /checkout, /profile, /admin/* with auth_present cookie check | VERIFIED | Read confirms exact matcher + 307 redirect to /login?returnTo=. T-04-02 grep-verified. |
| `sources/frontend/src/providers/AuthProvider.tsx` | useAuth hook + lazy-initializer hydration | VERIFIED | Exists; 04-01 commit f37bb62 fix landed. |
| `sources/frontend/src/components/ui/{Banner,Modal,RetrySection}/` | UI-SPEC Surface 1/2/3 | VERIFIED | All 3 directories present with .tsx + .module.css per `ls`. UI-SPEC contracts grep-verified in 04-02-SUMMARY. |
| `sources/frontend/src/app/not-found.tsx` | Vietnamese 404 page | VERIFIED | File exists per filesystem check. |
| `.planning/phases/04-.../04-UAT.md` | 12 rows × Observation × Pass/Fail × Notes | VERIFIED | 12 rows; status: complete; sign-off 4/4 boxes. |
| `sources/frontend/playwright.config.ts` + `e2e/uat.spec.ts` + `e2e/observations.json` + 12 screenshots | Repeatable UAT harness | VERIFIED | All listed in 04-03-SUMMARY key-files; commit 08ef751. |
| `sources/frontend/README.md` | Phase 4 Dev Workflow section | VERIFIED | 04-03 commit c6c32d3. |

### Key Link Verification (sample)

| From | To | Via | Status | Details |
|------|------|-----|--------|---------|
| services/http.ts | services/token.ts | getAccessToken + clearTokens imports | WIRED | Read http.ts line 14: `import { getAccessToken, clearTokens } from './token';` |
| services/http.ts | services/errors.ts | ApiError throw | WIRED | Read http.ts line 13 + line 89: imports + throws ApiError. |
| middleware.ts | auth_present cookie | req.cookies.get | WIRED | Read middleware.ts line 22: `const authPresent = req.cookies.get('auth_present')?.value;` |
| app/layout.tsx | AuthProvider + ToastProvider | JSX wrap around children | WIRED | 04-01-SUMMARY confirms; commit 4466080. |
| app/checkout/page.tsx | services/orders.createOrder | createOrder( call | WIRED | Read checkout/page.tsx line 19 + line ~85: imports + calls. |
| app/checkout/page.tsx | services/cart.readCart + clearCart | imports + calls | WIRED | Read checkout/page.tsx lines 12-18: imports readCart, clearCart, removeFromCart, updateQuantity. |
| app/checkout/page.tsx | Banner + Modal | JSX usage | WIRED | Read checkout/page.tsx lines 9-10: imports. UAT B1 visually confirms Banner; B2/B3 visually confirm Modal. |
| app/login/page.tsx | services/auth | login call | OVERRIDDEN | Mock submit per pre-wave deviation. setTokens + AuthProvider.login still wired. |
| app/cart/page.tsx | services/cart.readCart | readCart() + cart:change listener | WIRED | 04-02-SUMMARY grep-verified. |
| app/products/page.tsx | services/products.listProducts | listProducts call | WIRED | UAT A3 confirms network 200 to /api/products/products?size=24&sort=createdAt%2Cdesc. |

### Data-Flow Trace (Level 4) — the load-bearing finding

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| ProductCard.tsx | product.category.name, .rating, .reviewCount, .thumbnailUrl, .tags, .shortDescription | listProducts() → backend product-service | NO — backend returns thin DTO without these fields | HOLLOW (wired but data disconnected) |
| app/page.tsx (home) | products[] | listProducts({ sort: 'reviewCount,desc' / 'createdAt,desc' }) | YES (200 response) — but the data shape doesn't match what ProductCard consumes | FLOWING-BUT-MISMATCHED |
| app/products/page.tsx | products[] | listProducts({ ... }) | YES (200 response) — same shape mismatch | FLOWING-BUT-MISMATCHED |
| app/products/[slug]/page.tsx | product | getProductBySlug(slug) → /api/products/products/slug/{slug} | NO — endpoint returns HTTP 500 | DISCONNECTED |
| app/checkout/page.tsx | order (success path) | createOrder({items, shippingAddress, paymentMethod, note}) | NO — backend always 400 because body missing userId+status | DISCONNECTED |
| app/profile/page.tsx | orders[] | listMyOrders() | YES (200 response, empty list because A5 never persisted) | FLOWING |
| app/cart/page.tsx | cartItems[] | readCart() (localStorage) | YES (when cart is populated by addToCart) | FLOWING — but A4 cascade prevents add-to-cart from being reachable |

**Net:** every page's wiring is correct (Level 1+2+3 PASS), but the upstream data sources for the rich-DTO consumers do not produce real data → Level 4 fail. This is precisely the FE-01 gap.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `npm run build` exits 0 | (verified end of each plan) | exit 0 reported in 04-01/02/03 SUMMARYs | PASS |
| `npm run lint` exits 0 | (verified end of each plan) | exit 0 reported in 04-01/02/03 SUMMARYs | PASS |
| Middleware redirect smoke | `curl -I http://localhost:3000/checkout` (no cookie) → 307 Location: /login?returnTo=%2Fcheckout | Verified production-build smoke at end of 04-01 and 04-02 | PASS |
| Playwright UAT suite | `npx playwright test` against docker-compose stack + next start | 7/12 PASS, 5/12 FAIL (per 04-UAT.md) | MIXED — see SC#1/SC#2 |

Spot-checks not re-run during verification; relying on documented evidence in SUMMARYs and observations.json.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|----------------|-------------|--------|----------|
| FE-01 | 04-01, 04-02, 04-03 | Frontend API client aligns to documented contracts (URLs, DTOs, status codes, error format) | NEEDS-REWORK | Compile-time aligned; runtime gap (3 backend issues). REQUIREMENTS.md already flagged `status: needs-rework`. Closure planned via Phase 4.1. |
| FE-02 | 04-01, 04-02, 04-03 | Checkout and cart flows handle error cases gracefully (validation, stock, payment failure, auth) | MET | All 5 dispatcher branches verified per UAT (B1 real, B2/B3/B4a/B5 stubbed-but-contract-correct, B4b real). T-04-02/03/04/05 mitigations verified. REQUIREMENTS.md already flagged `status: met`. |

No orphaned requirements.

### Anti-Patterns Found (from 04-REVIEW.md, 7 warnings + 6 info)

| File | Line | Pattern | Severity | Impact on Phase 4 |
|------|------|---------|----------|-------------------|
| sources/frontend/src/app/cart/page.tsx | ~70 | `<Image src={item.thumbnailUrl}>` with no fallback when string is empty | WR-01 / Warning | Likely contributed to UAT FAIL on cart hydration when seed `thumbnailUrl=''`. Phase 4.1 should add `?.trim() ? url : '/placeholder.png'` fallback OR clean at the addToCart write boundary. |
| sources/frontend/src/app/checkout/page.tsx | ~253 | Same `<Image src=''>` pattern in summary | WR-01 / Warning | Same impact as above — fix as part of Phase 4.1 ProductCard hardening. |
| sources/frontend/src/services/http.ts | 62 | `JSON.parse(text)` not wrapped in try/catch | WR-02 / Warning | Could re-bite during Phase 4.1 gap closure if backend gateway returns HTML 5xx page; SyntaxError would bypass the dispatcher contract. Recommend folding the try/catch fix into Phase 4.1 (FE-side, low-risk) so the dispatcher branches stay intact when real 5xx arrives. |
| sources/frontend/src/services/http.ts | 79-85 | 401 redirect uses pathname only (drops `search`) | WR-03 / Warning | Mismatch with middleware.ts:24 which encodes pathname+search. UX bug only; not security. Defer to Phase 4.1 or Phase 5. |
| sources/frontend/src/components/ui/ProductCard/ProductCard.tsx | 35, 72, 95, 96, 103 | rich-DTO field access without null-guard | WR-04 / Warning | This IS the FE-01 gap — already in gaps[] above. WR-04 confirms code-side defense-in-depth is also missing even after backend ships. |
| sources/frontend/src/components/ui/Toast/Toast.tsx | 18 | `Date.now()` as id — collision risk | WR-05 / Warning | A11y/UX nice-to-have; defer. |
| sources/frontend/src/components/ui/Modal/Modal.tsx | 52-72 | No Tab focus trap | WR-06 / Warning | A11y regression; defer. |
| sources/frontend/src/app/register/page.tsx | 64 | No returnTo support | WR-07 / Warning | UX bug; defer. |
| 6× IN-01..IN-06 | various | Style smells / Playwright flakiness / `code: string` not union / `npx --yes` etc. | Info | Defer all six to Phase 5 cleanup. |

**Severity routing:**
- WR-01 (next/image src empty) — **route into Phase 4.1 plan list** because it likely already contributed to UAT FAIL noise and will re-bite once backend ships rich DTO with mostly-populated thumbnailUrl and a few legacy nulls.
- WR-02 (JSON.parse race) — **route into Phase 4.1** as a prerequisite cleanup; Phase 4.1 re-runs UAT and may stop services to verify B5, exactly the failure mode that exposes WR-02.
- WR-03..WR-07 + IN-01..IN-06 — defer to Phase 5 (cleanup phase).

### Human Verification Required

None. The Playwright walkthrough already provided observation evidence + the user spot-checked B1 + B2 visuals (both PASS). No additional human verification needed at this gate.

### Gaps Summary (narrative)

Phase 4 ships **strong wiring** — every artifact called for in the three PLAN frontmatters exists, compiles, lints, and is wired to the right collaborators. The HTTP tier is solid; the middleware is grep-verifiable and production-smoke-verified; the error-recovery dispatcher works against the designed contract; the UAT deliverable is committed with 12 rows of observations + screenshots + machine-readable JSON.

What blocks closure is a **runtime contract mismatch between the backend and the FE's expected DTO shape**, which only becomes visible when you actually walk the happy path against the real service stack:

1. **Backend product-service returns thin DTO; FE consumes rich.** ProductCard accesses `category.name`, `rating`, `reviewCount`, `thumbnailUrl`, `tags`, `shortDescription` — all undefined on the real response → unmount → page-level crash on home (A1), products list (A3), and the related-grid on product detail.

2. **Backend order-service createOrder rejects the FE's command shape.** Backend OrderUpsertRequest demands `userId` and `status` in the request body. FE sends a domain command. Every checkout submit returns 400 with `fieldErrors=[{field:userId},{field:status}]`. A5 fails permanently.

3. **Backend product-service `/products/slug/{slug}` returns 500.** A4 detail-page deep-link cannot load. Generic body — root cause masked by the Phase 3 `GlobalExceptionHandler.handleFallback` discarding Throwable (already documented in `.planning/debug/products-list-500.md`).

These three gaps are tightly coupled: they all resolve through one Phase 4.1 milestone with backend changes plus an FE re-run UAT. The 04-03-SUMMARY explicitly recommends three plans:

- **04.1-01:** backend product-service DTO enrichment + slug 500 fix.
- **04.1-02:** backend order-service createOrder command DTO + JWT-derived userId + server-side status default. Optionally wire createOrder → inventory-service.reserve to produce the real Q3 shape.
- **04.1-03:** FE re-run Playwright UAT against the new backend (no stubs for B2/B3/B4a/B5); capture real Q3 + Q4 shapes; expected 12/12 PASS.

Two FE-side cleanups should fold into Phase 4.1 to harden the dispatcher contract before re-run UAT:
- WR-01 next/image fallback for empty thumbnailUrl.
- WR-02 JSON.parse try/catch in services/http.ts so non-JSON 5xx bodies still flow through the ApiError contract.

Two MUST-HAVES are accepted via override (mock /auth/* carve-out, user-approved 2026-04-24).
Two open questions (Q3 stock-shortage shape, Q4 payment-fail code) are deferred to Phase 4.1 because they need backend integration that hasn't shipped.

Phase 4 status: **gaps_found**. Recommend `/gsd-plan-phase 4 --gaps` invocation to generate the 4.1 plan list above.

---

_Verified: 2026-04-25_
_Verifier: Claude (gsd-verifier)_
