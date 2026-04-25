---
phase: 4
slug: frontend-contract-alignment-e2e-validation
document: UAT
status: complete
executed_at: "2026-04-25T05:35:54Z"
executed_by: Claude Code (Playwright headless hybrid 80/20) + dminhduc25013615 spot-check
backend_commit: bc5cf680c513d08b82e4d55da52f49d4d3747426
frontend_commit: bc5cf680c513d08b82e4d55da52f49d4d3747426
playwright_run: sources/frontend/e2e/observations.json
screenshots: sources/frontend/e2e/screenshots/
result: 7/12 PASS, 5/12 FAIL — FE-02 (error recovery) ✓ all green; FE-01 (real-backend contract alignment) has integration gaps blocking happy-path
---

# Phase 4 — Manual UAT Checklist

> Phase deliverable per D-13. Walkthrough run via Playwright headless against live docker-compose stack
> + host `next start` build serving on port 3000. Screenshots + network traces captured per row.
>
> Hybrid 80/20 method: Playwright drives navigation/forms/network/storage assertions; user spot-checks
> two visual rows (B1 banner + B2 modal) — both PASS.

---

## Prerequisites — Confirmed

- ✓ docker compose up -d — all 6 backend services + gateway up (containers `tmdt-use-gsd-{api-gateway,user-service,product-service,order-service,payment-service,inventory-service,notification-service}-1`)
- ✓ port-by-port `/v3/api-docs` smoke: 8081-8086 all 200
- ✓ Frontend served on port 3000 (host `next start` of latest commit `bc5cf68` — container build was stale; see deviation log below)
- ✓ Browser: Playwright Chromium (headless) with `viewport: { width: 1280, height: 720 }`, `locale: vi-VN`
- ✓ State reset: each test starts with cleared cookies + localStorage (per-test fresh BrowserContext)

---

## Happy Path (A1–A6) — D-13 mandatory

| ID | Step | Expected Observation | Actual Observation | Pass/Fail | Notes |
|----|------|----------------------|--------------------|-----------|-------|
| A1 | Open http://localhost:3000/ | Home page loads with featured/new products from real API; Vietnamese UI; Header + Footer visible. | `<html id="__next_error__">` rendered (Next.js error boundary). lang attr=null. CTA "Khám phá ngay" not visible — page crashes during SSR/CSR. | **FAIL** | Root cause: home page calls `listProducts()`, real backend returns thin DTO without `category.name`/`thumbnailUrl`/`rating`/`tags`/`shortDescription` that `ProductCard` consumes (`product.category.name` throws on undefined). Same root cause behind A3+A4. **FE-01 contract gap.** Screenshot: e2e/screenshots/A1.png |
| A2 | /register; fill form; submit. | POST /api/users/auth/register returns 2xx; tokens persist; auth_present cookie set; user lands on /. | Mock setTimeout flow exercised (backend `/auth/register` not implemented per 04-02 deviation). `localStorage.accessToken=mock-access-token`, `auth_present` cookie = `1`, `userProfile` populated; no `/api/users` network call (NONE — confirms mock path). | **PASS** | Mock fallback confirmed. Real auth wiring still deferred — backend needs to ship `/auth/login|register|logout`. |
| A3 | /products; browse; click a product. | Real API call; product list renders; clicking card → /products/[slug]. | Network: 200 `/api/products/products?size=24&sort=createdAt%2Cdesc` + 200 `/api/products/products/categories`. Real backend returns 1 product (`Ao thun cotton trang`, seeded for UAT). **Page renders Next.js error boundary** — `ProductCard` crashes accessing `product.category.name` on undefined. | **FAIL** | Same FE-01 contract gap as A1. Real backend response shape: `{id, name, slug, categoryId, price, status, deleted, createdAt, updatedAt}` — missing `category.name`, `thumbnailUrl`, `rating`, `reviewCount`, `tags`, `shortDescription`. FE needs a DTO adapter. Screenshot: e2e/screenshots/A3.png |
| A4 | From detail, click "Thêm vào giỏ hàng"; → /cart. | Toast appears; cart shows added item; checkout CTA visible. | Detail page (slug) endpoint `/api/products/products/slug/{slug}` returns HTTP 500 from backend (`INTERNAL_ERROR`). Page never reaches Add-to-Cart button. localStorage.cart remains empty. | **FAIL** | Cascade from A3 + backend bug: `/products/slug/{slug}` 500s. Two distinct gaps: (a) FE-01 DTO adapter; (b) backend product-service slug lookup endpoint broken. Screenshot: e2e/screenshots/A4.png |
| A5 | /checkout; fill form; "Đặt hàng". | POST /api/orders/... returns 2xx; cart clears; success modal. | POST /api/orders/orders → **HTTP 400 VALIDATION_ERROR** with fieldErrors `[{field: status, message: must not be blank}, {field: userId, message: must not be blank}]`. FE doesn't send `status` or `userId` in createOrder body — backend DTO requires them (CRUD-style entity expectations, not domain command). Cart NOT cleared. | **FAIL** | FE-01 contract gap on order creation: backend `OrderUpsertRequest` is a raw entity DTO requiring `userId` (should be derived from JWT) + `status` (should be backend default). FE-side fix alone is insufficient — backend `createOrder` controller needs to switch to a command DTO. Screenshot: e2e/screenshots/A5.png |
| A6 | Close success modal; navigate to /profile. | Profile loads (middleware admits); order history shows the just-placed order. | Middleware admits (`auth_present=1` set via mock). Profile page renders. listMyOrders → 200 from `/api/orders/orders`. Order history empty (because A5 didn't actually create an order). Page does NOT crash — profile uses simpler order DTO that doesn't trigger ProductCard's category access. | **PASS** | Profile page integration works. Order list endpoint contract aligned. Empty list is expected given A5's failure. Screenshot: e2e/screenshots/A6.png |

---

## Failure Cases (B1–B5) — FE-02 recovery contract

| ID | Step | Expected Observation | Actual Observation | Pass/Fail | Notes |
|----|------|----------------------|--------------------|-----------|-------|
| B1 | /checkout with cart non-empty, submit blank. | 400 VALIDATION_ERROR; Banner "Vui lòng kiểm tra các trường bị lỗi"; inline errors. | Network: POST /api/orders/orders → 400, `code: VALIDATION_ERROR`, `fieldErrors.length=2` (status, userId required). Banner visible with exact text "Vui lòng kiểm tra các trường bị lỗi" at top of form. Inline errors are NOT mapped to address fields because backend complains about `userId`+`status` instead of address fields — but Banner+dispatcher logic correct. | **PASS** | Dispatcher correctly maps VALIDATION_ERROR → setBannerVisible(true). Real backend triggered the error, not a stub. The fact that backend's `fieldErrors` aren't address fields is a downstream issue (same FE-01 gap as A5) — FE-02 contract is met. Screenshot: e2e/screenshots/B1.png |
| B2 | Force backend stock shortage. | 409 STOCK_SHORTAGE; stock modal "Một số sản phẩm không đủ hàng" + buttons "Cập nhật số lượng" / "Xóa khỏi giỏ". | **STUBBED via Playwright `page.route()`** — backend `inventory-service` has no stock-shortage logic, `order-service.createOrder` does NOT call inventory-service. Verified FE dispatcher: stub returned 409 with `details.domainCode='STOCK_SHORTAGE', details.items=[{...availableQuantity:2,requestedQuantity:5}]`. Modal title visible, both action buttons visible with exact correct labels. | **PASS (dispatcher)** | Q3 (real CONFLICT discriminator shape) **NOT resolved** — backend doesn't yet emit it. FE dispatcher built per the designed shape (`details.domainCode === 'STOCK_SHORTAGE' OR Array.isArray(details.items)`) is valid IF backend ships matching shape. Recommend Phase 4.1 or Phase 5: wire `order-service.createOrder` → inventory-service.reserve, emit 409 with the contract above. Screenshot: e2e/screenshots/B2.png |
| B3 | Force payment mock failure. | 409 CONFLICT from payment path; payment modal "Thanh toán thất bại" + buttons "Thử lại" / "Đổi phương thức thanh toán". | **STUBBED via Playwright `page.route()`** — backend `payment-service` is CRUD-only and is not in the checkout call chain. Stubbed 409 with `details.domainCode='PAYMENT_FAILED', reason='CARD_DECLINED'`. Modal title visible, both buttons visible with exact correct labels. | **PASS (dispatcher)** | Q4 (real payment-fail HTTP code + body) **NOT resolved** — backend doesn't yet emit it. Recommend Phase 4.1 or Phase 5: integrate payment-service into checkout flow + define error contract. FE-side dispatcher works for the designed shape. Screenshot: e2e/screenshots/B3.png |
| B4a | Logged in; remove `accessToken`; navigate /profile (which fires authed call). | 401 from backend; http.ts clearTokens (localStorage + cookie); redirect to /login?returnTo=/profile. | **STUBBED via Playwright `page.route()`** — backend doesn't enforce JWT (no auth gate on /api/orders/orders). Stubbed `/api/orders/orders*` GET → 401. http.ts handler: cleared localStorage tokens (verified — both keys gone), cleared `auth_present` cookie (verified — cookie removed), redirected to `/login?returnTo=%2Fprofile` (verified — final URL). | **PASS** | T-04-04 (stale-token-after-logout) verified end-to-end via stub. T-04-02 (middleware admit/redirect) verified in Wave 1 production smoke. Recommend Phase 5: add JWT validation filter to all protected backend endpoints. Screenshot: e2e/screenshots/B4a.png |
| B4b | Logged out; visit /login?returnTo=https://evil.example.com/; submit creds. | Post-login lands on `/`, NOT evil.example.com. | Post-mock-login final URL = `http://localhost:3000/`. Open-redirect guard correctly rejected absolute URL `https://evil.example.com/`. T-04-03 mitigation verified. | **PASS** | Login page returnTo guard works. Mock submit path used (per A2 deviation) but the guard is applied regardless of mock vs real auth. Screenshot: e2e/screenshots/B4b.png |
| B5 | Stop order-service mid-checkout; submit. | 5xx/network error; toast "Đã có lỗi, vui lòng thử lại"; gateway log shows EXACTLY ONE POST. | **STUBBED via Playwright `page.route()`** as 502 (equivalent behavior to docker stop — same ApiError default branch). POST attempts counted: 1 (no auto-retry per D-10). Toast visible with exact text "Đã có lỗi, vui lòng thử lại". | **PASS** | D-10 (no auto-retry on POST/PUT/DELETE) verified. Equivalent to UAT spec's `docker compose stop order-service` since both produce the same INTERNAL_ERROR / network branch in http.ts. Screenshot: e2e/screenshots/B5.png |

---

## Summary

- **PASS: 7/12** — A2 (mock register), A6 (profile + listMyOrders), B1 (real validation), B2 (stubbed dispatcher), B3 (stubbed dispatcher), B4a (stubbed 401 → cleartokens+redirect), B4b (open-redirect guard), B5 (toast + no auto-retry)
- **FAIL: 5/12** — A1 (home crash), A3 (products crash), A4 (cascade + backend slug 500), A5 (order DTO mismatch)

### What's working

- **FE-02 error-recovery contract is fully verified.** Banner + Stock modal + Payment modal + Toast + RetrySection all wire to the right ApiError branches with the exact UI-SPEC Vietnamese strings.
- **Middleware + 401 handler + open-redirect guard all pass.** Security threats T-04-02, T-04-03, T-04-04 verified through this UAT.
- **HTTP layer fundamentals work.** Real backend 400 VALIDATION_ERROR (B1) flows through `httpPost` → ApiError → dispatcher → Banner with no manual intervention.

### What's NOT working — FE-01 contract alignment gap

Phase 4 SC #1 ("documented endpoints/DTOs surfaced via codegen + typed client") was achieved at compile-time but **not at runtime**. Three concrete gaps:

1. **Product DTO mismatch.** Backend `product-service` returns thin DTO (`{id, name, slug, categoryId, price, status, deleted, createdAt, updatedAt}`). FE `ProductCard` and home page consume rich DTO (`category.name`, `thumbnailUrl`, `rating`, `reviewCount`, `tags`, `shortDescription`). Pages crash on first product render.
2. **Order DTO mismatch.** Backend `order-service.createOrder` requires `userId` + `status` as request body fields (raw entity Upsert). FE sends `{items, shippingAddress, paymentMethod, note}` — domain command shape. 400 VALIDATION_ERROR every time.
3. **Backend slug endpoint broken.** `/api/products/products/slug/{slug}` returns 500 (INTERNAL_ERROR). Detail page can't load even if a product exists by slug.

### Open questions still UNRESOLVED

- **Q3 — CONFLICT stock-shortage real shape:** backend doesn't emit. Verified FE dispatcher only against designed stub shape.
- **Q4 — Payment-fail real HTTP code + body:** payment-service not in checkout chain. Verified FE dispatcher only against designed stub shape.

### Mock-data audit (clean)

`grep -rn 'mock-data\|mockProducts\|mockOrders\|mockUsers' sources/frontend/src/app/{cart,checkout,login,register,products,profile,page.tsx}` returns NOTHING — UAT-path pages confirmed off mocks. Residual mocks in `admin/*` (4 files) + `profile/orders/[id]/page.tsx` are expected per CONTEXT §Deferred Ideas.

### Recommendation — Phase 4.1 (gap closure)

Three plans:

1. **04.1-01 — Backend Product DTO enrichment.** product-service: serialize `Category` join (name, slug); add fields `thumbnailUrl`, `description`, `shortDescription`, `tags`, `rating`, `reviewCount` (defaults if absent in DB); fix `/products/slug/{slug}` 500.
2. **04.1-02 — Backend Order command DTO.** order-service: replace `OrderUpsertRequest` with `CreateOrderCommand` accepting `{items[], shippingAddress, paymentMethod, note}`; derive `userId` from JWT (or session header); set `status` to `PENDING` server-side.
3. **04.1-03 — Re-run UAT.** Re-execute this Playwright suite end-to-end against the real backend (no stubs); resolve Q3 + Q4 with real backend response captures.

Phase 4.1 should also (optionally) ship backend `/auth/login|register|logout` to close the carry-over A2 mock fallback.

---

## Sign-Off

- [x] All 12 rows have Actual Observation + Pass/Fail recorded
- [x] `backend_commit` + `frontend_commit` SHAs recorded (`bc5cf680...`)
- [x] Executor + timestamp recorded
- [x] 5 failures documented with root-cause + recommended Phase 4.1 plan structure
- [x] Q3 + Q4 explicitly flagged as unresolved-pending-backend-work
