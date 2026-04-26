# Phase 9: Residual Closure & Verification - Context

**Gathered:** 2026-04-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Đóng 4 carry-over residual gaps từ v1.1 (AUTH-06 middleware mở rộng, AUTH-07 password change, UI-02 admin dashboard real KPIs, TEST-01 Playwright re-baseline) trước khi mở các feature phases v1.2 (Phase 10+). KHÔNG mở scope mới — pure closure + verification.

</domain>

<decisions>
## Implementation Decisions

### Middleware (AUTH-06)

- **D-01:** Canonical middleware = `sources/frontend/src/middleware.ts`. **Xóa** `sources/frontend/middleware.ts` (root, stale duplicate có matcher rộng nhưng thiếu logic /403). Next.js prefer `src/middleware.ts` khi tồn tại — root file đang dead code và gây nhầm lẫn.
- **D-02:** Mở rộng matcher tại src/middleware.ts thành `['/admin/:path*', '/account/:path*', '/profile/:path*', '/checkout/:path*']`. KHÔNG include `/api/*` trong matcher → không cần early-return cho `/api/users/auth/*` (matcher chỉ liệt kê page routes).
- **D-03:** Giữ nguyên logic /403 redirect cho non-ADMIN truy cập `/admin/*` (D-09 từ Phase 6). Logic auth_present + user_role cookie pattern KHÔNG đổi.

### Stats endpoints (UI-02)

- **D-04:** 3 endpoints per-svc tự định nghĩa response shape (KHÔNG generic `{count: N}`, KHÔNG rich payload):
  - `GET /api/products/stats` → `{totalProducts: int}`
  - `GET /api/orders/stats` → `{totalOrders: int, pendingOrders: int}`
  - `GET /api/users/stats` → `{totalUsers: int}`
- **D-05 [REVISED 2026-04-26]:** Admin-only gating qua **path convention `/admin/*/stats` + manual JWT role check** trong controller (parse Bearer token → reject 403 nếu role!=ADMIN). KHÔNG dùng `@PreAuthorize` vì codebase chưa setup Spring Security (verified: zero `@PreAuthorize` hits trong user-svc, không có `SecurityFilterChain`). Endpoints: `/api/products/admin/stats`, `/api/orders/admin/stats`, `/api/users/admin/stats` (gateway match `/api/{svc}/admin/**` đã có). Spring Security setup defer cho future hardening phase. Intent admin-only của D-05 vẫn được đáp ứng. Non-admin call → 403.
- **D-06:** "Pending orders" = `orderStatus = PENDING` ONLY (không gộp SHIPPING/PAID). Field name `pendingOrders` rõ ràng.
- **D-07:** Endpoint scope service-level (mỗi service expose `/stats` riêng) — KHÔNG cross-service backend aggregation. FE Promise.allSettled 3 endpoints qua gateway.

### Admin dashboard scope (UI-02)

- **D-08:** **Trim** về đúng 4 KPI required (Total products, Total orders, Total users, Pending orders). **Xóa** khỏi `app/admin/page.tsx`:
  - `totalRevenue` card
  - "Đơn hàng gần đây" recent orders table
  - "Tổng quan nhanh" quick stats panel (pending/shipping/lowStock)
  - mockOrders/mockProducts/mockUsers arrays + `formatPrice`/Order/Product/User imports không dùng
- **D-09:** Loading skeleton + error fallback **per-card independent**. Dùng `Promise.allSettled` (KHÔNG `Promise.all`) — 1 endpoint fail không block 3 cards còn lại. Mỗi card có 3 state: loading skeleton → success render / error fallback ('--' với retry icon).

### Password change UX (AUTH-07)

- **D-10:** Sau khi đổi password thành công: **giữ session hiện tại, chỉ hiển thị toast** "Đã đổi mật khẩu". KHÔNG force logout, KHÔNG rotate JWT (token cũ vẫn valid 24h). Token rotation pattern defer cho future milestone nếu cần.
- **D-11:** Wrong oldPassword → backend trả 422 với error code `AUTH_INVALID_PASSWORD`, FE hiển thị field-level error tại field "Mật khẩu hiện tại". Endpoint dedicated `POST /api/users/me/password` (KHÔNG cho password trong PATCH chung).

### TEST-01 re-baseline strategy

- **D-12:** **Rewrite** theo v1.1 features (KHÔNG fix-in-place uat.spec.ts cũ, KHÔNG wipe). Tách thành nhiều file:
  - `e2e/auth.spec.ts` — register / login / logout / role gate
  - `e2e/admin-products.spec.ts` — admin CRUD products
  - `e2e/admin-orders.spec.ts` — admin list / detail / status update
  - `e2e/admin-users.spec.ts` — admin PATCH fullName/phone/roles + soft-delete
  - `e2e/order-detail.spec.ts` — `/profile/orders/:id` full breakdown (4-column items table, shippingAddress, paymentMethod)
  - `e2e/uat.spec.ts` — đánh dấu legacy (giữ làm reference Phase 4 era hoặc rename `*.legacy.spec.ts.bak`).
- **D-13:** Setup global storageState fixture: `e2e/global-setup.ts` thực hiện login user thường + login admin → save cả 2 storageState files. Tests reuse qua `test.use({ storageState: 'admin.json' })` — tránh login lặp lại mỗi test.
- **D-14:** Coverage scope = auth + admin CRUD + order detail (~12 tests). KHÔNG redundant shopping flow (đã có ở uat.spec.ts cũ làm reference). KHÔNG smoke-only — vẫn cover validation errors + role denied edge cases.
- **D-15:** Run model = manual local docker + observations.json evidence. Developer chạy `docker compose up -d` → `npx playwright test` → commit observations.json + screenshots như pattern v1.1. **KHÔNG CI/GitHub Actions integration** trong Phase 9 (defer).

### Claude's Discretion

- Cụ thể tên cookie/header response cho stats endpoints (Cache-Control, ETag) — Claude chọn theo pattern existing.
- Skeleton component design (CSS shimmer vs Suspense fallback variant) — Claude chọn consistent với UI hiện tại.
- Error fallback icon/copy — Claude chọn (vd icon ⚠ + "Không tải được" + retry button).
- Zod schema cho password form (min length, complexity rule) — Claude chọn theo industry default (min 8, ít nhất 1 letter + 1 number).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents PHẢI đọc các file dưới trước khi plan/implement.**

### Project-level
- `.planning/PROJECT.md` — visible-first priority, Vietnamese, B2C e-commerce stub demo
- `.planning/REQUIREMENTS.md` §AUTH-06, §AUTH-07, §UI-02, §TEST-01 — full requirement spec
- `.planning/ROADMAP.md` §Phase 9 — Goal + 4 Success Criteria (5 conditions phải TRUE)
- `.planning/STATE.md` — locked decisions carry-over từ v1.1

### Research artifacts (v1.2)
- `.planning/research/STACK.md`
- `.planning/research/FEATURES.md`
- `.planning/research/ARCHITECTURE.md`
- `.planning/research/PITFALLS.md` — đặc biệt §Pattern auth + admin gating
- `.planning/research/SUMMARY.md`

### Codebase intel
- `.planning/codebase/CONVENTIONS.md` — code style, error envelope pattern
- `.planning/codebase/INTEGRATIONS.md` — gateway routes, service-to-service
- `.planning/codebase/TESTING.md` — Playwright setup hiện tại

### Existing code (đụng tới Phase 9)
- `sources/frontend/src/middleware.ts` — canonical middleware (mở rộng matcher)
- `sources/frontend/middleware.ts` — stale duplicate (xóa)
- `sources/frontend/src/app/admin/page.tsx` — UI-02 wire real data + trim extras
- `sources/frontend/e2e/uat.spec.ts` — legacy reference (Phase 4 era)
- `sources/frontend/playwright.config.ts` — config base cho global setup mới

### Prior phase context (v1.1)
- Phase 6 D-07/D-08/D-09/D-10 — middleware auth_present + user_role cookie pattern + /403 vs /login redirect
- Phase 7 UI-01 admin CRUD — `@PreAuthorize` pattern mẫu
- Phase 4 UAT — observations.json evidence pattern

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`src/middleware.ts`** — pattern `auth_present` + `user_role` cookie + /403 redirect đã có. Chỉ cần mở matcher.
- **`@PreAuthorize("hasRole('ADMIN')")`** — pattern admin gating đã dùng ở admin CRUD v1.1, reuse cho 3 stats endpoints.
- **`ApiErrorResponse` envelope + traceId** (v1.0 baseline) — reuse cho 422 `AUTH_INVALID_PASSWORD` response.
- **rhf + zod pattern** — Phase 10 sẽ thiết lập foundation, nhưng AUTH-07 password form trong Phase 9 có thể dùng pattern tối thiểu (form + zod + 3 fields). Coordinate KHÔNG conflict với Phase 10.
- **Skeleton/loading pattern** từ admin CRUD v1.1 — reuse cho 4 KPI cards.

### Established Patterns
- **Per-service migration namespace**: schema migrations V3,V4,V5 (xem ROADMAP Pre-Phase Setup). Phase 9 KHÔNG migrate schema (residual closure thuần code/UI).
- **Gateway routing order**: `user-service-me` constraint là Phase 10 lock — Phase 9 chỉ thêm route `POST /api/users/me/password` PHẢI đứng trước `user-service-base` để tránh match `/api/users/{id}` với id="me/password".
- **observations.json + screenshots evidence** từ Phase 4 UAT — TEST-01 tiếp tục pattern này.

### Integration Points
- Middleware affect: `/admin/*`, `/account/*`, `/profile/*`, `/checkout/*` — phải verify direct visit `/profile/orders` (chưa login) → 307 redirect.
- Stats endpoints qua gateway: 3 routes cần thêm vào gateway config nếu chưa public — verify tại `gateway/application.yml` (hoặc tương đương).
- E2E global-setup phải login qua **real backend** (KHÔNG stub) — depends on backend `up` trước khi test chạy.

</code_context>

<specifics>
## Specific Ideas

- "Đã cập nhật" / "Đã đổi mật khẩu" — Vietnamese toast copy.
- Field-level error tại password form: "Mật khẩu hiện tại không đúng" cho AUTH_INVALID_PASSWORD.
- Empty state nếu KPI = 0 vẫn render số "0" (KHÔNG hide card) — admin biết là zero thật chứ không phải loading.
- Retry icon trên error fallback card — click → re-fetch chỉ endpoint đó (KHÔNG reload page).

</specifics>

<deferred>
## Deferred Ideas

- **Token rotation sau password change** — defer cho future milestone. Hiện token 24h là acceptable risk cho dự án thử nghiệm.
- **Admin dashboard extras** (totalRevenue, recent orders table, lowStock alert) — defer. Có thể quay lại trong v1.3+ nếu admin UX request.
- **CI/GitHub Actions Playwright integration** — defer. Manual local docker run đủ cho v1.2.
- **Suspense + ErrorBoundary RSC pattern cho 4 cards** — defer. Per-card useState + Promise.allSettled đơn giản hơn cho scope hiện tại.
- **Negative-lookahead matcher / early-return guard cho `/api/*`** — không cần vì matcher chỉ list page routes.
- **Email change verification flow** — đã defer trong REQUIREMENTS Future, KHÔNG part of AUTH-07.

</deferred>

---

*Phase: 09-residual-closure-verification*
*Context gathered: 2026-04-26*
</content>
</invoke>