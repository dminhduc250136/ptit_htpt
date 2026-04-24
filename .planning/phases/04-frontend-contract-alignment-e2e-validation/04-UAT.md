---
phase: 4
slug: frontend-contract-alignment-e2e-validation
document: UAT
status: pending-walkthrough   # flipped to `complete` at end of Task 2
executed_at: ""               # ISO timestamp set during walkthrough
executed_by: ""               # dev name
backend_commit: ""            # git rev-parse HEAD at walkthrough time
frontend_commit: ""           # git rev-parse HEAD at walkthrough time
---

# Phase 4 — Manual UAT Checklist

> Phase deliverable per D-13: committed manual walkthrough of the full shopping flow
> (happy path A1..A6) plus the five recovery failure cases (B1..B5) on a live
> docker-compose stack with the Next.js dev server.
>
> Row IDs here mirror `04-VALIDATION.md §Manual-Only Verifications` and
> `04-CONTEXT.md D-13`. Vietnamese copy strings are locked by
> `04-UI-SPEC.md §Copywriting Contract`.

---

## Prerequisites

- `docker compose up -d` — all 6 services + gateway + frontend images running (gateway on 8080; users 8081; products 8082; orders 8083; payments 8084; inventory 8085; notifications 8086)
- `curl http://localhost:8081/v3/api-docs` returns JSON (smoke health). Repeat for ports 8082, 8083, 8084, 8085, 8086. Gateway `8080/v3/api-docs` may 404 — that is expected (springdoc is per-service; gateway has no aggregated spec).
- Frontend dev server: `cd sources/frontend && npm run dev` → http://localhost:3000
- Browser: Chrome or Firefox with devtools open (Console + Network + Application tabs) for localStorage + network inspection
- `localStorage` + cookies for `localhost:3000` cleared before starting (Application → Storage → Clear site data)
- Record frontmatter `backend_commit` + `frontend_commit` via `git rev-parse HEAD` (single monorepo — same SHA for both)
- Record frontmatter `executed_at` via `date -u +"%Y-%m-%dT%H:%M:%SZ"` and `executed_by` with the dev's name

---

## Happy Path (A1–A6) — D-13 mandatory

| ID | Step | Expected Observation | Actual Observation | Pass/Fail | Notes |
|----|------|----------------------|--------------------|-----------|-------|
| A1 | Open http://localhost:3000/ | Home page loads with featured/new products from real API (no "loading forever"); Vietnamese UI; Header + Footer visible. | | | |
| A2 | Click /register; fill form with unique email + valid password; submit. | POST /api/users/auth/register returns 2xx; tokens land in localStorage (`accessToken`, `refreshToken`, `userProfile`); presence cookie `auth_present=1` set; user lands on / (or /login — depending on backend flow, either accepted). NOTE: if backend /auth/* endpoints still unshipped (see 04-02-SUMMARY §Deviations), mock submit path runs — Actual Observation should flag "mock flow" and still verify token/cookie population. | | | |
| A3 | Navigate to /products; browse; click a product. | Products list renders from real API (observe Network tab: request goes to /api/products/...); clicking card routes to /products/[slug]; detail page renders product. | | | |
| A4 | From product detail, click "Thêm vào giỏ hàng"; toast "Đã thêm vào giỏ hàng" appears; navigate to /cart. | Cart page shows the just-added item (not mock seeds); quantity + price correct; summary total correct. | | | |
| A5 | Click "Tiến hành thanh toán" → /checkout. Fill shipping form with valid values; pick paymentMethod; click "Đặt hàng". | POST /api/orders/... returns 2xx; cart clears (localStorage['cart'] removed); success modal with "Đặt hàng thành công!" + order code visible. | | | |
| A6 | Close success modal; navigate to /profile. | Profile page (protected by middleware — no redirect since we're logged in); order history section shows the just-placed order via listMyOrders. | | | |

---

## Failure Cases (B1–B5) — FE-02 recovery contract

| ID | Step | Expected Observation | Actual Observation | Pass/Fail | Notes |
|----|------|----------------------|--------------------|-----------|-------|
| B1 | At /checkout with cart non-empty, submit form with ≥1 required field blank. | Response is HTTP 400 with `code: VALIDATION_ERROR` and `fieldErrors[]`; top Banner "Vui lòng kiểm tra các trường bị lỗi" appears; each empty field shows inline error text; no toast; no modal. | | | |
| B2 | Reduce a cart product's backend stock below cart quantity (via admin UI OR direct DB edit OR any scripted mechanism). Submit /checkout. | Response is HTTP 409 `CONFLICT` with stock-shortage shape (record exact `details.domainCode` + `details.items[]` shape in Notes). Stock modal "Một số sản phẩm không đủ hàng" opens; lists affected items with name + availableQuantity + requestedQuantity; two buttons visible: "Cập nhật số lượng" + "Xóa khỏi giỏ". Click "Cập nhật số lượng" → cart quantities adjust and modal closes. Retry checkout → succeeds. | | | |
| B3 | Force payment mock failure (use whatever mechanism payment-service exposes — specific amount, env flag, or temporary code edit; document in Notes). Submit /checkout. | Response is HTTP 409 CONFLICT from payment path (record exact response body in Notes). Payment modal "Thanh toán thất bại" opens; body text "Giao dịch không thành công…"; two buttons: "Thử lại" + "Đổi phương thức thanh toán". Click "Thử lại" → retries POST /api/orders; if still fails, modal reopens. Click "Đổi phương thức thanh toán" → modal closes; user selects different method in form. | | | |
| B4a | Logged in. In devtools: `localStorage.removeItem('accessToken')`; keep the `auth_present` cookie. Navigate to any protected resource via a page that makes an authed call (e.g., /profile). | Page issues call; backend returns 401; http.ts wrapper calls clearTokens() (clears both localStorage AND auth_present cookie); redirects to `/login?returnTo=/profile`. No toast. After login, user lands back on /profile with order history. | | | |
| B4b | Logged out (no cookie). Navigate directly to `http://localhost:3000/login?returnTo=https://evil.example.com/`. Submit valid credentials. | Post-login, router.replace uses '/' (open-redirect blocked per T-04-03). User does NOT land on evil.example.com. | | | |
| B5 | Logged in. At /checkout, stop `order-service` in docker-compose: `docker compose stop order-service`. Submit /checkout form with valid fields. | Response is HTTP 5xx or network error via gateway. Toast "Đã có lỗi, vui lòng thử lại" appears (once). In gateway log, confirm exactly ONE POST to /api/orders (no auto-retry — D-10). Inline retry: check /products or /profile loaded sections that failed during the same window show RetrySection with "Thử lại" button. Restart service: `docker compose start order-service`; submit again → succeeds. | | | |

---

## Sign-Off

- [ ] All 11 rows pass, OR failing rows have filed follow-up tickets with IDs
- [ ] `backend_commit` + `frontend_commit` SHAs recorded
- [ ] Executor name + timestamp recorded
