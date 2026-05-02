# tmdt-use-gsd — Project Charter

## What This Is

Dự án thử nghiệm GSD workflow trên codebase e-commerce laptop (Spring Boot microservices + Next.js). Mục đích: stress-test multi-milestone / multi-phase / audit-verify loop của GSD trên một hệ thống e-commerce realistic — codebase là vehicle để demo workflow, không phải production product.

**Core Value:** Demo end-to-end shopping experience hoạt động với real data ở mọi điểm user nhìn thấy, đồng thời rèn quy trình GSD từ planning → execute → verify → archive.

## Context

- **Domain:** E-commerce (B2C — individual consumers)
- **Stage:** Post-v1.0 MVP — đã có shopping flow stub-verified end-to-end, đang nâng dần lên real-data
- **Key Users:** Laptop buyers (browse → cart → checkout → payment) + Admin (CRUD catalog/orders)
- **Tech Stack:** Spring Boot (microservices), Next.js (frontend), Docker Compose, PostgreSQL
- **Existing Codebase:** 7 microservices, frontend app, complete docker-compose setup

## Requirements

### Validated

✓ **User Service** — Existing. Handles authentication, user profiles, registration  
✓ **Product Service** — Existing. Product catalog, browse, search, reviews  
✓ **Order Service** — Existing. Order management, tracking, cart operations  
✓ **Payment Service** — Existing. Payment processing integration  
✓ **Inventory Service** — Existing. Stock tracking, availability  
✓ **Notification Service** — Existing. Email/notification dispatch  
✓ **API Gateway** — Existing. Service routing and orchestration  
✓ **Frontend App** — Existing. Next.js UI with product pages, checkout, admin panels  
✓ **CRUD Completeness Baseline** — Completed in Phase 02 across User/Product/Order/Payment/Inventory/Notification services

✓ **Database Foundation** — Postgres + JPA + Flyway + seeded data từ FE mocks (DB-01..06) — v1.1
✓ **Real auth flow** — Backend `/api/users/auth/{register,login,logout}` + JWT HS256 + FE form gỡ mock + session persist (AUTH-01..05) — v1.1
✓ **Admin + Search real CRUD** — `/search` keyword + admin/products|orders|users qua gateway (UI-01, UI-03, UI-04) — v1.1
✓ **Cart → Order persistence** — ProductEntity.stock persist + OrderItemEntity per-row + shippingAddress/paymentMethod + FE order detail full breakdown (PERSIST-01..03) — v1.1

✓ **v1.2 Residual closure** — AUTH-06 middleware 4-route matcher + AUTH-07 password change + UI-02 admin dashboard real KPI + TEST-01 Playwright re-baseline 14 tests — Phase 9
✓ **Profile editing** — GET/PATCH /api/users/me, form rhf+zod tại /profile/settings (ACCT-03) — Phase 10
✓ **Address book + checkout integration** — V4 addresses CRUD cap 10/user + ADDRESS_LIMIT_EXCEEDED 422 + AddressPicker JSONB snapshot (ACCT-02/05/06) — Phase 11
✓ **Reviews & ratings** — V4 reviews + V5 avg_rating cached + verified-buyer cross-service eligibility (REV-01/02/03) — Phase 13
✓ **Basic search filters** — JPQL cast IS NULL + FilterSidebar component + brand multi-select + price range debounce 400ms (SEARCH-01/02) — Phase 14
✓ **Public polish** — Hero next/image priority + Featured CSS scroll-snap + 3-tier stock badge + brand-based breadcrumb + 4 Playwright smoke E2E (PUB-01/02/03/04 + TEST-02) — Phase 15

### Active (v1.3 — TBD)

Chưa define — chạy `/gsd-new-milestone` để bootstrap. Backlog candidates xem `Deferred (v1.3+)` bên dưới.

### Deferred (v1.3+)

- [ ] **ACCT-01 Wishlist** — Phase 12 SKIPPED v1.2 scope trim, V5 migration reserved
- [ ] **REV-04 Author edit/delete reviews** — scope trim v1.2
- [ ] **SEARCH-03 rating filter + SEARCH-04 URL state + in-stock + clear-all** — scope trim v1.2
- [ ] **PUB-03 lightbox + axe-core a11y gate** — scope trim v1.2 (thumbnail swap đủ)
- [ ] **TEST-02 full E2E suite (8+ tests)** — scope trim v1.2 (smoke 4 đủ closure)
- [ ] **ACCT-04 avatar upload** — Deferred D-08 từ Phase 10 (multipart Thumbnailator scope cut)
- [ ] **Nyquist VALIDATION.md** — carry-over từ v1.1 (Phase 6 + Phase 8 + v1.2 phases)
- [ ] **Backend hardening carry-over** — D1, D2, D6, D7, D8, D9, D10, D12, D13, D14, D17 (chỉ pick visible)
- [ ] **Multi-step checkout** — tách Shipping → Payment → Review
- [ ] **Recently viewed / Related products** — rule-based recommendations

### Out of Scope

- Real payment gateway integration (mock đủ cho dự án thử nghiệm) — focus là architecture pattern, không phải commerce thật
- Advanced features (recommendations, analytics, AI) — giữ scope tập trung core patterns
- Production-grade infrastructure (load balancing, failover) — MVP đủ
- Mobile app — web-only MVP
- Real-time features (WebSockets, live notifications) — polling/events đủ
- Backend hardening invisible to end-user (CSP, server-side price re-fetch, gateway JWT claim verification, observability/tracing) — defer cho đến khi visible features đủ

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Microservices over monolith | Demo được distributed pattern cho mục đích thử nghiệm GSD trên hệ thống realistic | Chosen |
| Spring Boot + Java | Industry standard, codebase đã có sẵn | Locked |
| **v1.1 visible-first priority** | Dự án thử nghiệm — ưu tiên cái user nhìn thấy, defer backend hardening invisible | Locked v1.1 |
| Next.js for frontend | Modern React, SSR capability, good developer experience | Locked |
| PostgreSQL | Relational data model fits e-commerce well | Locked |
| Docker/Compose for local dev | Replicates production patterns locally | Locked |
| Focus on happy path first | Get core flow working, then add error handling | Pending |
| Synchronous service calls | Simpler than async/event-driven for MVP | Pending |

## Success Criteria

- [ ] All 7 microservices compile and run without errors
- [ ] Frontend loads without errors, connects to backend
- [ ] User can complete full shopping flow: register → browse → add to cart → checkout → payment → order confirmation
- [ ] Each service has well-defined REST endpoints with documented contracts
- [ ] Docker Compose deploys entire system with one command
- [ ] Code follows Spring Boot and TypeScript conventions
- [ ] Error handling is consistent across services
- [ ] Basic validation on inputs prevents common exploits

## Constraints & Assumptions

**Constraints:**
- Dự án thử nghiệm GSD — KHÔNG cần production-grade hardening
- Ưu tiên features end-user nhìn thấy; defer backend hardening / security / observability nếu invisible
- Local development environment (Docker Compose, không K8s)

**Assumptions:**
- Existing codebase có project structure đúng
- Database migrations handled
- Frontend/backend communication REST-based
- Mock data acceptable cho external integrations (payment gateway giả lập)

## Backlog Observations

From codebase analysis, areas worth monitoring:
- Service-to-service authentication/authorization patterns
- Data consistency across services (eventual consistency handling)
- API versioning strategy as system evolves
- Performance optimization (N+1 queries, caching)
- Comprehensive test coverage (currently minimal)

## Current Milestone: v1.3 — TBD (planning pending)

**Status:** v1.2 SHIPPED 2026-05-02 (passed — 17/17 active REQs satisfied). Tag `v1.2` annotated local. v1.3 scope chưa define — chạy `/gsd-new-milestone` hoặc `/gsd-roadmap` để bootstrap.

**v1.3 backlog candidates (deferred from v1.2):**
- ACCT-01 wishlist (Phase 12 plans cần re-plan với V5 migration)
- REV-04 author edit/delete + SEARCH-03 rating filter + SEARCH-04 URL state + in-stock + clear-all
- PUB-03 lightbox + axe-core a11y gate
- TEST-02 full E2E suite (8+ tests)
- ACCT-04 avatar upload (deferred D-08 backlog)

**Phase numbering:** tiếp tục từ Phase 16 (KHÔNG reset).

## Current State

**Last shipped:** v1.2 — UI/UX Completion (2026-05-02, passed: 17/17 active REQs satisfied)

6/6 active phases complete + 1 SKIPPED (Phase 12 wishlist scope trim) + 24/24 plans done. Audit re-run 09:30 UTC sau Phase 14 merge → verdict PASSED. Tag `v1.2` annotated local pending user push.

Cumulative foundation (v1.0 + v1.1 + v1.2):
- 6 services + gateway emit unified `ApiErrorResponse` envelope với traceId propagation
- Springdoc Swagger UI + OpenAPI codegen pipeline (FE 6 typed modules)
- **Postgres 16 + JPA + Flyway** trên 5 services (V1 baseline + V2 dev seed + V3 stock + V4 reviews/addresses + V5 avg_rating cached)
- **Real auth flow**: BCrypt + JWT HS256 24h, AuthProvider hydration, middleware 4-route gate (`/admin|/account|/profile|/checkout`), password change endpoint
- **Admin CRUD thật**: products + orders + users (PATCH fullName/phone/roles + soft-delete) + admin dashboard 4 real KPI cards
- **Cart → Order persistence**: ProductEntity.stock + OrderItemEntity per-row + shippingAddress JSONB snapshot + paymentMethod
- **Account features**: profile editing rhf+zod, address book CRUD cap 10/user + ADDRESS_LIMIT_EXCEEDED, order filter (status + date range + keyword)
- **Reviews & ratings**: verified-buyer eligibility cross-service, XSS-safe render, avg_rating cached cols
- **Search filters**: JPQL cast IS NULL pattern, FilterSidebar component (brand multi-select + price range debounce 400ms)
- **Public polish**: Hero next/image priority WebP, Featured CSS scroll-snap carousel, PDP brand-based breadcrumb, 3-tier stock badge color-coded, thumbnail strip + main image swap
- **E2E**: Playwright suite (14 v1.1 baseline tests + 4 v1.2 smoke tests covering homepage/checkout/review/profile)
- **3 git tags**: `v1.0`, `v1.1`, `v1.2`

## Next Milestone Goals (v1.3 — TBD)

Pending scope definition. Top candidates từ v1.2 deferral:

- ACCT-01 wishlist (V5 migration ready)
- REV-04 + SEARCH-03 + SEARCH-04 advanced discovery
- PUB-03 lightbox + a11y hardening
- TEST-02 full E2E suite
- ACCT-04 avatar upload (multipart + Thumbnailator)
- Backend hardening D1..D17 (revisit nếu có triggering event)

Chạy `/gsd-new-milestone` để bootstrap scope cụ thể.

<details>
<summary>Previous milestones (shipped)</summary>

**v1.0 — MVP Stabilization** (2026-04-25)
- Complete CRUD endpoints across all microservices (consistent patterns)
- Swagger/OpenAPI documentation for every service and gateway
- Frontend-backend API contract alignment (DTOs, status codes, error formats)
- 11/11 REQs MET, Playwright 12/12 PASS

**v1.1 — Real End-User Experience** (2026-04-26)
- C0 Database Foundation — Postgres + JPA + Flyway + seed từ FE mocks
- C1 Real auth flow — JWT HS256 + FE form gỡ mock
- C2 Admin + Search real CRUD qua gateway
- C3 Cart → Order persistence visible — full breakdown
- 15/19 SATISFIED + 4 PARTIAL (residual defer v1.2 — đều close trong v1.2)

**v1.2 — UI/UX Completion** (2026-05-02)
- Phase 9 Residual closure (AUTH-06/07, UI-02, TEST-01 close v1.1 partials)
- Phase 10 User-svc schema + profile editing (ACCT-03)
- Phase 11 Address book + order history filtering (ACCT-02/05/06)
- Phase 13 Reviews & ratings verified-buyer (REV-01/02/03)
- Phase 14 Basic search filters brand+price (SEARCH-01/02)
- Phase 15 Public polish + smoke E2E (PUB-01/02/03/04, TEST-02)
- 17/17 active REQs satisfied (Phase 12 wishlist SKIPPED scope trim)
</details>

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition:**
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone:**
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-02 — Milestone v1.2 UI/UX Completion SHIPPED (passed: 17/17 active REQs satisfied; tag v1.2 annotated local pending user push). Next: v1.3 scope planning.*
