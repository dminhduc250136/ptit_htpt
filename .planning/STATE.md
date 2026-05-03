---
gsd_state_version: 1.0
milestone: v1.3
milestone_name: Catalog Realism & Commerce Intelligence
status: executing
last_updated: "2026-05-03T12:00:00Z"
last_activity: 2026-05-03
progress:
  total_phases: 7
  completed_phases: 4
  total_plans: 14
  completed_plans: 16
  percent: 57
---

## Current Position

Phase: 19-ho-n-thi-n-admin-charts-low-stock (19) — COMPLETED
Plan: 4 of 4 — All Plans 01+02+03+04 COMPLETE
Status: Phase 19 Plan 04 done (FE recharts@3.8.1 + 5 fetchers + 6 components + admin/page extend + 2 Playwright specs). Next: Phase 20 Coupon system
Last activity: 2026-05-02

```
Progress: [█████░░░░░] 57% (4/7 phases complete)
```

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-05-02 — Current Milestone: v1.3 Catalog Realism & Commerce Intelligence)

**Core value:** Demo end-to-end shopping experience hoạt động với real data ở mọi điểm user nhìn thấy, đồng thời rèn quy trình GSD từ planning → execute → verify → archive.

**Current focus:** Phase 19 next — Hoàn Thiện Admin Charts + Low-Stock (ADMIN-01..05)

## Resume Cheat-Sheet

- Roadmap: `.planning/ROADMAP.md` (7 phases — Phase 16-22)
- Requirements: `.planning/REQUIREMENTS.md` (27 active REQs, tất cả mapped)
- Research: `.planning/research/SUMMARY.md` (HIGH confidence — codebase inspection trực tiếp)
- Milestone v1.0 archive: `.planning/milestones/v1.0-ROADMAP.md` + REQUIREMENTS.md
- Milestone v1.1 archive: `.planning/milestones/v1.1-ROADMAP.md` + REQUIREMENTS.md (audit gaps_found)
- Milestone v1.2 archive: `.planning/milestones/v1.2-ROADMAP.md` + REQUIREMENTS.md + audit (passed)
- Git tags: `v1.0`, `v1.1`, `v1.2`
- Project priority: visible-first (memory `feedback_priority.md`)
- Language: Vietnamese (memory `feedback_language.md`)

## Performance Metrics

| Milestone | Phases | Plans | REQs | Result |
|-----------|--------|-------|------|--------|
| v1.0 | 4 | 14 | 11/11 | PASSED |
| v1.1 | 4 | 22 | 15/19 SATISFIED + 4 PARTIAL | PASSED (gaps deferred) |
| v1.2 | 6 (+1 SKIP) | 24 | 17/17 | PASSED |
| v1.3 | 7 planned | TBD | 0/27 | In progress |
| Phase 17-s-a-order-detail-items P01 | 5min | 2 tasks | 2 files |
| Phase 17-s-a-order-detail-items P02 | 3min | 2 tasks | 1 file |
| Phase 17-s-a-order-detail-items P03 | 2min | 2 tasks | 2 files |
| Phase 17-s-a-order-detail-items P04 | 4min | 2 tasks | 2 files |
| Phase 18-storage-audit-cart-db P01 | 25min | 3 tasks | 10 files |
| Phase 18-storage-audit-cart-db P02 | 15min | 2 tasks | 2 files |
| Phase 18-storage-audit-cart-db P03 | 12min | 2 tasks | 4 files |
| Phase 18-storage-audit-cart-db P04 | 15min | 3 tasks | 3 files |
| Phase 18-storage-audit-cart-db P05 | 10min | 2 tasks | 7 files |
| Phase 18-storage-audit-cart-db P06 | 10min | 2 tasks | 1 file |
| Phase 19-ho-n-thi-n-admin-charts-low-stock P01 | 25min | 2 tasks | 9 files |
| Phase 19-ho-n-thi-n-admin-charts-low-stock P02 | 15min | 2 tasks | 7 files |
| Phase 19-ho-n-thi-n-admin-charts-low-stock P03 | 12min | 2 tasks | 10 files |
| Phase 19-ho-n-thi-n-admin-charts-low-stock P04 | 18min | 3 tasks | 15 files |
| Phase 20-coupons P03 | 12min | 2 tasks | 7 files |

## Decisions (active v1.3 locks)

**Phase 20 Plan 03 decisions (2026-05-03):**

- COUP-04 BE order integration hoàn tất: OrderCrudService.createOrderFromCommand inject CouponRedemptionService, gọi atomicRedeem trong cùng @Transactional cha (D-08, D-12). Server compute discountAmount từ subtotal qua CouponPreviewService.computeDiscount (D-10, KHÔNG tin client). Snapshot 2 field discountAmount + couponCode lên OrderEntity → cuối cùng saved
- COUP-03 BE preview endpoint: CouponPreviewController POST /orders/coupons/validate (D-13) + CouponPreviewControllerIT 5 cases (happy, already-redeemed 409, anonymous skip-check, missing code 400, unknown code 404)
- CreateOrderCommand record extend với field couponCode nullable (D-12) — backward compat khi null/blank
- OrderDto + 2 field discountAmount + couponCode (D-23, D-24) — FE Plan 20-06 sẽ render
- OrderEntity.setTotal helper mới (Rule 3 fix): plan đề xuất dùng update() reset 4 field — không phù hợp; setTotal scope hẹp + bumps updatedAt
- D-25 race condition IT: 6 cases gồm R1 (2 thread khác user, maxTotalUses=1 → 1 success + 1 CONFLICT) + R2 (2 thread cùng user → 1 success + 1 ALREADY_REDEEMED qua UNIQUE violation, usedCount=1 sau rollback) + 4 bonus (no coupon, happy, atomic rollback unknown, server-compute discount D-10) — chứng minh SC #3 race-safe
- Maven CLI vẫn chưa khả dụng trên Windows env, defer verify cho CI/local mvn — pattern đồng nhất với Plans 20-01, 20-02

**Phase 19 Plan 04 decisions (2026-05-02):**

- ADMIN-01..05 FE consume hoàn tất: install recharts@3.8.1 --save-exact (D-11 lock no caret), 5 typed fetchers services/charts.ts, lib/chartFormat.ts (STATUS_COLORS D-12 + statusLabel D-13 VN + Intl.NumberFormat/DateTimeFormat 'vi-VN')
- 6 components admin/: ChartCard generic 3-state wrapper (D-14), 4 chart components (Revenue/TopProducts/StatusDistribution/UserSignups), LowStockSection với click row → router.push('/admin/products?highlight={id}') (D-10)
- admin/page.tsx extend (KHÔNG rewrite): KPI row → time-window dropdown (default 30d D-06) → 2x2 charts grid → low-stock full-width (D-07 layout). 5 useCallback loaders với deps [range] cho 3 charts có time-window, deps [] cho status pie + low-stock (D-06 pie KHÔNG bị range)
- 2 Playwright smoke specs: admin-charts.spec.ts + admin-low-stock.spec.ts (reuse admin storageState từ global-setup.ts Phase 9 D-13). Spec syntax verified bằng tsc; runtime execution defer cho /gsd-verify-work hoặc UAT manual khi docker+browser binaries ready
- Rule 1 type fix Recharts 3.8.1: Tooltip Formatter signature `Formatter<ValueType, NameType>` accept `value: ValueType | undefined`, KHÔNG accept narrow `: number` annotation per RESEARCH code. Cast Number(v)/String(iso) inside callback body
- Rule 2 lint fix: eslint-disable `@next/next/no-img-element` cho LowStockSection thumbnail Unsplash CDN (precedent v1.2 SEED-03, KHÔNG cần next/image domain config)
- Phase 19 hoàn tất 4/4 plans → 5/5 ADMIN-01..05 COMPLETED → ready /gsd-verify-work

**Phase 19 Plan 03 decisions (2026-05-02):**

- ADMIN-05 BE product-svc layer hoàn tất: ProductRepository.findLowStock @Query (stock<:threshold ORDER BY stock ASC, soft-delete auto-filtered) + LowStockService (LOW_STOCK_THRESHOLD=10 D-08, CAP=50 D-09, record LowStockItem D-10) + ProductBatchService (record ProductSummary match Plan 19-01 wire format, JpaRepository.findAllById built-in)
- 2 controllers: AdminChartsController GET /admin/products/charts/low-stock + AdminProductBatchController POST /admin/products/batch (path /admin/products/batch không /charts/batch — gateway rewrite catch-all)
- ProductEntity dùng accessor record-style p.id() / p.name() — verified entity convention, RESEARCH gợi ý p.getId() bị deviation (Rule 1 fix)
- @RequestBody(required=false) + null guard cho batch controller — defensive null body handling (Rule 2)
- 17 test cases written (3 IT findLowStock + 2 unit LowStockService + 4 unit ProductBatchService + 4 IT AdminCharts + 4 IT AdminProductBatch) — Maven CLI vẫn chưa khả dụng trên Windows env, defer verify cho Wave check
- Gateway routes existing /api/products/admin/** đã catch-all cover /charts/low-stock và /batch — KHÔNG modify api-gateway

**Phase 19 Plan 02 decisions (2026-05-02):**

- ADMIN-04 BE user-svc layer hoàn tất: Range enum per-svc copy (KHÔNG shared module — pattern theo JwtRoleGuard precedent) + UserRepository.aggregateSignupsByDay @Query (FUNCTION('DATE', u.createdAt) Postgres dialect) + UserChartsService Java gap-fill (0L cho ngày trống, D-05) + AdminChartsController @GetMapping(/signups) với JwtRoleGuard.requireAdmin
- 13 test cases written (5 RangeTest + 2 UserRepositorySignupsIT + 6 AdminChartsControllerIT) — Maven CLI vẫn chưa khả dụng trên Windows env, defer verify cho Wave check
- Plan 02 INDEPENDENT với Plan 01 (different svc, different repo) — parallel-executable cùng Wave 1
- Gateway routes existing /api/users/admin/** đã catch-all cover /api/users/admin/charts/** automatically — KHÔNG modify api-gateway

**Phase 19 Plan 01 decisions (2026-05-02):**

- ADMIN-01/02/03 BE order-svc layer hoàn tất: Range enum (D7/D30/D90/ALL parse + toFromInstant) + 3 OrderRepository @Query (revenue/top/status) + ProductBatchClient cross-svc + OrderChartsService (Java gap-fill BigDecimal.ZERO) + AdminChartsController (3 GET endpoints)
- D-03 + Pitfall #4 áp dụng nghiêm: top-products forward Bearer authHeader xuống ProductBatchClient → product-svc batch endpoint cũng gate JwtRoleGuard. Verified qua AdminChartsControllerIT.verify(productBatchClient).fetchBatch(anyList(), eq("Bearer " + adminToken))
- Gateway routes existing /api/orders/admin/** đã catch-all cover /api/orders/admin/charts/** — KHÔNG modify api-gateway
- Maven CLI không có trên Windows env này — tests đã viết (19 cases tổng) nhưng chưa chạy; defer verify cho `/gsd-verify-work` hoặc Plan 04 FE consume khi có Maven+Docker

**Carry-over từ v1.2:**

- Phase numbering tiếp tục KHÔNG reset (v1.3 → Phase 16+)
- Visible-first priority giữ nguyên
- Backend hardening (D1..D17) defer cho đến khi có triggering event (ngoại lệ: storage audit có thể surface security issue)
- ApiErrorResponse + traceId envelope; Swagger/OpenAPI codegen; Postgres + JPA + Flyway 5 services; auth thật JWT HS256; admin CRUD qua gateway; FE typed services; rhf+zod pattern; Playwright suite

**Phase 18 Plan 06 decisions (2026-05-02):**

- STORE-01 closed: grep confirms 4 localStorage keys (cart/userProfile/accessToken/refreshToken) + 2 cookie keys (auth_present/user_role). Classify xong vao bang audit 18-SUMMARY.md
- STORE-03 closed: no additional user-data leaks found beyond cart. Wishlist/recently-viewed/search-history KHONG ton tai trong FE codebase hien tai
- UAT 4 truths auto-approved: workflow._auto_chain_active=true, code review + static analysis thay the manual browser test
- Phase 18 COMPLETED: 6/6 plans done, STORE-01/02/03 closed, 18-SUMMARY.md committed

**Phase 18 Plan 01 decisions (2026-05-02):**

- CartController.java reset thành stub (Plan 02 replace hoàn toàn) — file cũ reference CartUpsertRequest đã bị xóa cùng với InMemoryCartRepository
- CartItemEntity.equals/hashCode dựa trên id only (không traverse lazy collection) — tranh LazyInitializationException
- KHÔNG có unit_price_at_add trong cart_items — cart hiển thị live price, chỉ snapshot khi tạo order
- upsertAddQuantity native SQL ON CONFLICT DO UPDATE — idempotent ADD semantics (D-05)

**v1.3 locks (2026-05-02 — locked từ research + user answers):**

- **Seed catalog**: ~100 SP / 5 categories (điện thoại, laptop, chuột, bàn phím, tai nghe), brand realistic (Apple/Samsung/Logitech/Razer/Sony/...), ảnh Unsplash CDN `?fm=webp&q=80` (precedent v1.2 P15). Flyway V7 product-svc Spring profile `dev`.
- **Storage audit scope**: TOÀN FE codebase (grep localStorage + sessionStorage), classify (user-data / UI-pref / auth-token), migrate user-data → DB per user_id. Cart confirmed localStorage-only (dead code `InMemoryCartRepository` unused).
- **Admin charts**: 4 loại — revenue/time (area), top products (bar), order status pie (donut), signups (line) + low-stock alert `stock < 10`. Stack: `recharts@3.8.1`.
- **Review polish**: REV-04 (author edit/delete = soft-delete `deleted_at`) + sort by newest/rating DESC/ASC + admin moderation (hide/unhide = `hidden BOOLEAN` column). KHÔNG helpful votes (defer v1.4).
- **Coupon**: 2 loại (% off + fixed amount), min order + expiry + max usage, 1 lần/coupon/user (`UNIQUE CONSTRAINT coupon_redemptions(coupon_id, user_id)`), 1 mã/đơn (KHÔNG stack). Admin CRUD `/admin/coupons` + FE checkout input. Race-safe atomic UPDATE. Flyway V3 order-svc.
- **Chatbot AI**: Claude API MVP, model `claude-haiku-4-5`. Customer FAQ + product Q&A + recommendation. Streaming UI (native ReadableStream, không Vercel AI SDK). Chat history persist Postgres `chat_svc` schema (raw pg driver). Prompt caching bắt buộc từ ngày 1. Login required (NOT guest). Sliding window 10 turns. Admin "suggest reply" — manual confirm. KHÔNG agentic tool-use. Stack: `@anthropic-ai/sdk@0.92.0`. Phase này dùng `/gsd-ai-integration-phase`.
- **Order detail bug**: Root cause xác định — admin page có hardcoded string "Chi tiết sản phẩm..."; `AdminOrder` interface thiếu `items[]`. FE fix chính, BE verify DTO đã đúng.
- **Cart→DB placement**: order-svc Flyway V4 (`carts` + `cart_items`). FE `services/cart.ts` refactor API-first. Guest merge idempotent `ON CONFLICT DO UPDATE`.
- **Chat persistence placement**: Next.js API route + raw pg driver trực tiếp `chat_svc` schema. KHÔNG tạo Spring Boot microservice mới.

## Flyway V-number Reservations (confirmed từ research)

| Service | Version | Purpose | Phase |
|---------|---------|---------|-------|
| product-svc | V101 | Seed ~100 sản phẩm trong db/seed-dev/ (Spring profile `dev` only) — APPLIED 2026-05-02 | Phase 16 |
| order-svc | V3 | Coupons + coupon_redemptions tables | Phase 20 |
| order-svc | V4 | Carts + cart_items tables | Phase 18 |
| chat_svc | — | Schema init qua Next.js raw pg driver (không Flyway) | Phase 22 |

*Note: user-svc V5 reserved cho ACCT-01 wishlist (v1.4). product-svc V8 optional review sorts index nếu cần.*

## Deferred Items (carry-over từ v1.2 + v1.3 scope locks)

| Category | Item | Status |
|----------|------|--------|
| debug | products-list-500 | root_cause_found — carry-over |
| uat_gap | Phase 06/07/09/10/11 *-HUMAN-UAT.md | partial / pending manual UAT |
| verification_gap | Phase 06/07/09/10/11 *-VERIFICATION.md | human_needed |
| v1.4+ | ACCT-01 wishlist | SKIPPED v1.2 Phase 12, V5 migration reserved |
| v1.4+ | SEARCH-03/04 rating filter + URL state | scope trim v1.2 |
| v1.4+ | ACCT-04 avatar upload | Deferred D-08 từ Phase 10 |
| v1.4+ | TEST-02-FULL 8+ E2E tests | scope trim v1.2 |
| v1.4+ | COUP-06 coupon stacking | scope lock v1.3 |
| v1.4+ | AI-06 agentic chatbot tool-use | scope lock v1.3 |
| v1.4+ | REV-07 helpful votes | scope lock v1.3 |
| v1.4+ | STORE-04 auth-token httpOnly cookie | visible-first defer |

## Blockers

Không có blocker.

## Accumulated Context

- Project: tmdt-use-gsd — dự án thử nghiệm GSD workflow (Spring Boot microservices + Next.js + API gateway + Docker Compose).
- Foundation v1.0 + v1.1 + v1.2 reuse được: ApiErrorResponse + traceId envelope; Swagger/OpenAPI codegen; Postgres + JPA + Flyway 5 services; auth thật JWT HS256; admin CRUD qua gateway; FE typed services; rhf+zod pattern; Playwright E2E suite (14 baseline + 4 smoke); reviews verified-buyer cross-service; FilterSidebar pattern; M3 design tokens.
- Visible-first priority applied: feature invisible → defer; visible → ship.
- v1.2 closed 2026-05-02 với verdict PASSED (17/17 active REQs). Tag `v1.2` annotated local.
- Memory: Vietnamese chat/docs/commits; visible-first priority; dự án thử nghiệm GSD KHÔNG phải PTIT/HTPT assignment.
- Pitfall quan trọng v1.3: coupon double-redemption (atomic UPDATE + UNIQUE constraint); chatbot context window blowup (sliding 10 turns + prompt cache); Flyway seed prod isolation (V7 vào seed/dev path); cart merge race condition (idempotent upsert + FE useRef flag); prompt injection từ reviews (XML tag isolation).

## Next Steps

1. `/gsd-plan-phase 19` → Execute Phase 19: Admin Charts + Low-Stock (ADMIN-01..05)
2. `/gsd-plan-phase 20` → Execute Phase 20: Hệ Thống Coupon (COUP-01..05)
3. `/gsd-plan-phase 21` → Execute Phase 21: Hoàn Thiện Reviews (REV-04..06)
4. `/gsd-ai-integration-phase 22` → Execute Phase 22: AI Chatbot Claude API MVP (dùng AI integration workflow thay plan-phase chuẩn)

**Completed:**
- Phase 16: Seed Catalog Hiện Thực — **COMPLETED 2026-05-02** (3/3 plans, SEED-01..04)
- Phase 17: Sửa Order Detail Items — **COMPLETED 2026-05-02** (4/4 plans, ORDER-01 + ADMIN-06)
- Phase 18: Kiểm Toán Storage + Cart→DB — **COMPLETED 2026-05-02** (6/6 plans, STORE-01/02/03 closed)
- Phase 19 Plan 01: order-svc admin chart endpoints — **COMPLETED 2026-05-02** (ADMIN-01/02/03 BE layer done; FE Plan 04 sẽ consume)
- Phase 19 Plan 02: user-svc admin /signups chart endpoint — **COMPLETED 2026-05-02** (ADMIN-04 BE layer done)
- Phase 19 Plan 03: product-svc admin /low-stock + /batch endpoints — **COMPLETED 2026-05-02** (ADMIN-05 BE layer done + cross-svc enrichment helper cho Plan 01)
- Phase 19 Plan 04: FE admin charts grid + low-stock — **COMPLETED 2026-05-02** (recharts@3.8.1 + 5 fetchers + 6 components + admin/page extend + 2 Playwright specs)
- Phase 19: Hoàn Thiện Admin Charts + Low-Stock — **COMPLETED 2026-05-02** (4/4 plans, ADMIN-01..05 closed)
