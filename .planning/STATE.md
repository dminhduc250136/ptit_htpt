---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_plan: 6
status: phase-4-gap-closure-complete-awaiting-user-approval
last_updated: "2026-04-25T12:13:14.000Z"
last_activity: 2026-04-25 -- Phase 04 plan 04-06 COMPLETE (no auto-commit; user stages manually). Frontend hardening Wave 2 landed: WR-01 thumbnailUrl placeholder fallback + WR-02 JSON.parse try/catch + WR-04 ProductCard null guards + unitPrice/X-User-Id plumbing trong checkout flow + regenerated codegen từ 04-04+04-05 OpenAPI emit. Playwright UAT re-run 12/12 PASS (vs 7/12 PASS, 5/12 FAIL trong 04-03). 4 stubs (Q3/Q4/B4a/B5) preserved per Phase 5 deferral và explicitly documented trong 04-UAT.md Phase 5 Carry-Over section. Phase 4 SC#1+SC#2+SC#3 all met at runtime; ready to close pending user approval.
progress:
  total_phases: 4
  completed_phases: 3
  total_plans: 14
  completed_plans: 14
  percent: 100
---

## Current Position

Phase: 04 (frontend-contract-alignment-e2e-validation) — Gap-closure Wave 2 COMPLETE (04-04 + 04-05 + 04-06 all done; phase ready to close)
Plan: 6 of 6 (ALL DONE)
Current Plan: 6
Total Plans: 06
Status: Phase 04 plan 04-06 COMPLETE on disk (no commits — user stages manually per MEMORY.md no-autocommit rule). FE hardening (WR-01/02/04) + cart-unitPrice + X-User-Id plumbing + regenerated codegen + Playwright re-run 12/12 PASS. SC#1 (FE-01 contract alignment) + SC#2 (E2E shopping flow) + SC#3 (FE-02 error recovery) all met at runtime. Phase 4 ready to close — user runs `gsd-sdk query phase.complete 04` (or equivalent) after stage+commit.
Last activity: 2026-04-25 — Executed 04-06 Wave 2 (frontend half). Task 1 (WR-01 thumbnailUrl fallback in cart/checkout/ProductCard + new /placeholder.png 1×1 PNG asset; WR-04 null-coalescing in ProductCard). Task 2 (WR-02 JSON.parse try/catch in services/http.ts; httpPost/httpPut/httpPatch extended với extraHeaders param; npm run gen:api regenerated products.generated.ts +78/-1 + orders.generated.ts +23/-2). Task 3 (services/orders.createOrder takes userId arg + sends X-User-Id header; checkout/page.tsx plumb unitPrice: i.price + user?.id từ useAuth(); CreateOrderRequest type updated). Task 4 (Playwright re-run against real backend; 12/12 PASS sau khi fix Turbopack production hydration timing với page.reload + waitForFunction + cart:change dispatch). Task 5 (04-UAT.md updated in place: result: 12/12 PASS; rows A1/A3/A4/A5 flipped; new Phase 5 Carry-Over section). Task 6 (checkpoint auto-approved per workflow.auto_advance config). 3 deviations: 1 Rule 3 blocker (pre-existing ESLint `any` in uat.spec.ts:288 fixed inline), 2 Rule 1 bugs (Turbopack hydration race + selector strict-mode → all in test infrastructure). All verification gates pass; build + lint green throughout.

## Resume Cheat-Sheet

- Wave 1 complete: commits 8957411, afb0757, 4466080, f37bb62, 2828e70 on branch `develop`.
- Wave 2 complete: commits a1bd832, 5b75a23, 65d2895 on `develop`. See `04-02-SUMMARY.md`.
- Wave 3 complete-with-gaps: commits c6c32d3, 65c29ce, 08ef751, 58cfd7b + final docs commit on `develop`. See `04-03-SUMMARY.md`.
- **Phase verification done (2026-04-25):** `04-VERIFICATION.md` returned `gaps_found` — 2/3 SC met (SC#3); SC#1 + SC#2 failed at runtime due to FE-01 contract gap.
- **Gap-closure planning done (2026-04-25):** 3 new plans (04-04, 04-05, 04-06) created and verified by `gsd-plan-checker` (PASSED).
- **Plan 04-04 COMPLETE on disk (2026-04-25, no auto-commit):** backend half of FE-01 landed in product-service. See `04-04-SUMMARY.md`.
- **Plan 04-05 COMPLETE on disk (2026-04-25, no auto-commit):** order-service half of FE-01 landed. See `04-05-SUMMARY.md`.
- **Plan 04-06 COMPLETE on disk (2026-04-25, no auto-commit):** FE hardening + Playwright re-run landed. See `04-06-SUMMARY.md`. **12/12 PASS** observations.json.
- **All Phase 4 files awaiting user manual stage+commit (consolidated):**
  - **Backend Wave 1 (04-04 product-service):** `api/GlobalExceptionHandler.java`, `service/ProductCrudService.java`, `web/ProductController.java`, `pom.xml` (added spring-boot-starter-test scope=test), `src/test/.../ProductControllerSlugTest.java` (NEW).
  - **Backend Wave 1 (04-05 order-service):** `service/OrderCrudService.java`, `web/OrderController.java`, `src/test/.../OrderControllerCreateOrderCommandTest.java` (NEW).
  - **Frontend Wave 2 (04-06):** `src/components/ui/ProductCard/ProductCard.tsx` (WR-01 + WR-04), `src/app/cart/page.tsx` (WR-01), `src/app/checkout/page.tsx` (WR-01 + useAuth + unitPrice + user?.id), `src/services/http.ts` (WR-02 + extraHeaders), `src/services/orders.ts` (X-User-Id header), `src/types/index.ts` (CreateOrderRequest unitPrice), `public/placeholder.png` (NEW 1×1 PNG), `e2e/uat.spec.ts` (spec hardening + ESLint fix), `e2e/observations.json` (rewritten by re-run), `e2e/screenshots/*.png` (12 refreshed + A3-detail NEW), `src/types/api/products.generated.ts` (regenerated +78/-1), `src/types/api/orders.generated.ts` (regenerated +23/-2).
  - **Plan metadata:** `04-04-SUMMARY.md`, `04-05-SUMMARY.md`, `04-06-SUMMARY.md` (NEW), `04-UAT.md` (updated in place — result: 12/12 PASS + Phase 5 Carry-Over section), `.planning/STATE.md`, `.planning/ROADMAP.md`.
- **Phase close:** User runs `gsd-sdk query phase.complete 04` (or equivalent ROADMAP/STATE update) after stage+commit. This plan does NOT auto-mark the phase complete.
- See `.planning/phases/04-frontend-contract-alignment-e2e-validation/04-WAVE-STATUS.md` for full context.
- Environment note: run `export PATH="/c/Users/DoMinhDuc/AppData/Roaming/npm:$PATH"` before any `gsd-sdk` call (npm global bin not on system PATH).
- Build/test note: `mvn` not on Windows host PATH; use `MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/workspace" -v "$HOME/.m2:/root/.m2" -w //workspace maven:3.9-eclipse-temurin-17 mvn …`. To rebuild service container: `docker compose up -d --no-deps --build product-service` (~10s warm-up). For FE Playwright re-run: `cd sources/frontend && npm run build && npm run start &` then `npx playwright test e2e/uat.spec.ts --reporter=list`.

## Decisions

- (for this milestone)
- Typed HTTP tier + openapi-typescript codegen + middleware foundation landed (Phase 04-01). Auth endpoints deferred — backend does not expose /auth/* yet.
- 04-02: mock login/register carve-out — backend /auth/* not shipped; setTokens + AuthProvider hydration + T-04-03 returnTo guard still landed. Real auth call deferred.
- 04-02: CONFLICT discriminator = details.domainCode === 'STOCK_SHORTAGE' OR Array.isArray(details.items); payment modal is the else branch. Real shape to be captured in 04-03 UAT.
- 04-03: UAT walkthrough produced 7/12 PASS, 5/12 FAIL — all failures trace to FE-01 contract gap (backend thin DTO vs FE rich type) + backend slug 500 + order DTO mismatch. FE-02 dispatcher all green. Phase 4.1 gap-closure plan structure recommended in 04-03-SUMMARY.md.
- 04-04: product-service nay emit rich `ProductResponse` (category{name,slug}, thumbnailUrl, rating, reviewCount, tags, …) chỉ trên READ paths (list, getById, getBySlug); admin upsert paths (POST/PUT) vẫn trả raw `ProductEntity` để không phá Phase 02 CRUD smoke. `CategoryEntity.slug` không tồn tại → mapper dùng slugify-fallback trên category.name() (Phase 5 sẽ persist column slug thật). Slug 404 reuse `handleResponseStatus` envelope chuẩn — không thêm handler mới. Sibling-service observability rollout (5 services) deferred to Phase 5.
- 04-05: order-service `POST /orders` đổi DTO từ raw entity (`OrderUpsertRequest`) sang domain command (`CreateOrderCommand`) match FE checkout body shape `{items[], shippingAddress, paymentMethod, note}`. `userId` derive server-side từ `X-User-Id` header (`@RequestHeader required=false` + service-layer null-check để dùng existing `handleResponseStatus` envelope cho 400). `status` default `"PENDING"`, `totalAmount` compute từ `items[].quantity * items[].unitPrice` server-side — FE không gửi cả 3 field này. Admin path (`AdminOrderController` `/admin/orders` + `OrderController.PUT /orders/{id}`) giữ nguyên `OrderUpsertRequest` để Phase 02 CRUD smoke không regress. OrderEntity record vẫn aggregate-only (không persist per-item rows / shippingAddress / paymentMethod) — Phase 5 candidate. T-04-05-01 (X-User-Id header trust) + T-04-05-02 (client-supplied unitPrice) accepted cho MVP scope; Phase 5 hardening replace với JWT-claim derivation tại gateway + product-service price re-fetch. Q3 (real STOCK_SHORTAGE) + Q4 (real PAYMENT_FAILED) vẫn deferred — plan 04-06 sẽ keep Playwright stubs cho B2/B3.
- 04-06: FE hardening Wave 2 landed — WR-01 thumbnailUrl `?.trim() ? ... : '/placeholder.png'` fallback uniformly across cart/page.tsx + checkout/page.tsx + ProductCard.tsx; WR-02 try/catch around `JSON.parse(text)` trong services/http.ts (non-JSON 5xx → ApiError('INTERNAL_ERROR')); WR-04 null-coalescing trong ProductCard cho `category?.name`, `rating ?? 0`, `reviewCount ?? 0`. `httpPost`/`httpPut`/`httpPatch` extended với optional `extraHeaders` param (httpGet/httpDelete unchanged). `services/orders.createOrder(body, userId?)` attaches X-User-Id header. checkout/page.tsx plumb `unitPrice: i.price` per item + `user?.id` từ useAuth(). FE codegen regenerated (`products.generated.ts` +78/-1, `orders.generated.ts` +23/-2). Playwright UAT re-run **12/12 PASS** (vs 7/12 PASS, 5/12 FAIL trong 04-03). 4 stubs (Q3/Q4/B4a/B5) + A4 stock + A2 /auth/* preserved per Phase 5 deferral, explicitly documented trong 04-UAT.md "Phase 5 Carry-Over" section. Phase 4 SC#1+SC#2+SC#3 all met at runtime; ready to close pending user approval.

## Blockers

- **Backend `/auth/*` endpoints still missing** (carried over from Wave 1). Login + register in Wave 2 kept mock submit per user-approved deviation — `services/auth.ts` untouched, `setTokens()` + `AuthProvider.login()` still populate tokens + cookie + auth state. Real `login()` / `register()` calls deferred to a follow-up phase when backend ships `POST /api/users/auth/login` and `POST /api/users/auth/register`. See `04-02-SUMMARY.md` §Deviations.
- **Phase 4 cannot close until FE-01 gap closure runs (Phase 4.1).** UAT (04-03) surfaced runtime contract mismatches: (a) product DTO thin vs rich, (b) order DTO raw entity Upsert vs domain command, (c) backend `/products/slug/{slug}` returns 500. FE-side fix alone is insufficient; backend work required. See `04-03-SUMMARY.md` §FE-01 gaps surfaced + §Recommended Phase 4.1 plan structure.

## Accumulated Context

- Project is brownfield: 7 existing microservices + API gateway + Next.js frontend + Docker Compose.
- This milestone focuses on API completeness/consistency (CRUD + Swagger/OpenAPI) and frontend contract alignment.

**Planned Phase:** 3 (Validation & Error Handling Hardening) — 2 plans — 2026-04-23T14:48:22.059Z

**Phase 03 completion:** Service-side hardening verified in user-service (8 tests) + order-service (6 tests); gateway contract verified in api-gateway (6 tests). A latent bug was caught by the gateway regression test — Spring Cloud Gateway's `NotFoundException` extends `ResponseStatusException`, so the original branch order returned 503 for missing routes instead of 404. Branch order corrected in `GlobalGatewayErrorHandler`.
