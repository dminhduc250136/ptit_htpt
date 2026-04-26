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

### Active (v1.2 — TBD)

_Run `/gsd-new-milestone` để define scope. Carry-over candidates từ v1.1 partial gaps:_

- [ ] **AUTH-06 closure** — middleware.ts matcher mở rộng `/account|/profile|/checkout` (currently chỉ `/admin`)
- [ ] **UI-02 closure** — admin landing dashboard (`app/admin/page.tsx`) rewire sang real services (currently KPI = 0 trên empty mock)
- [ ] **Nyquist VALIDATION.md** — Phase 6 + Phase 8 (defer được nếu visible-first vẫn priority)
- [ ] **Backend hardening carry-over** — D1, D2, D6, D7, D8, D9, D10, D12, D13, D14, D17 (chỉ pick visible)
- [ ] **Behavioral E2E re-baseline** — Playwright suite update cho v1.1 features (auth/admin/order detail)

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

## Current State

**Last shipped:** v1.1 — Real End-User Experience (2026-04-26)

15/19 requirements SATISFIED + 4/19 PARTIAL (DB-05 và PERSIST-01 closed cuối milestone qua commit `346092b`; AUTH-06 middleware narrow + UI-02 admin landing dashboard defer v1.2). 4 phases / 22 plans / 289 commits trong 2 days. Audit ghi nhận `gaps_found` proceeded với residual partial làm tech debt.

Foundation đã có (cumulative v1.0 + v1.1):
- 6 services + gateway emit unified `ApiErrorResponse` envelope với traceId propagation
- Springdoc Swagger UI + OpenAPI codegen pipeline (FE 6 typed modules)
- **Postgres 16 + JPA + Flyway** trên 5 services (V1 baseline + V2 dev seed từ FE mocks)
- **Real auth flow**: BCrypt + JWT HS256 24h, FE login/register pages, AuthProvider hydration, middleware admin role gate
- **Admin CRUD thật**: products (V2 brand/thumbnail/originalPrice), orders (list+detail+status), users (PATCH fullName/phone/roles + soft-delete)
- **Cart → Order persistence**: ProductEntity.stock (V3), OrderItemEntity per-row + shippingAddress JSON + paymentMethod, FE 4-column items table
- FE typed HTTP tier + ApiError dispatcher (5 failure branches) + cart stock snapshot/clamp
- 2 git tags: `v1.0`, `v1.1`

## Next Milestone Goals (v1.2 — TBD)

Run `/gsd-new-milestone` để define scope. Suggested directions (visible-first priority):

- **AUTH-06 + UI-02 closure** — middleware matcher mở rộng + admin landing dashboard rewire (residual gaps từ v1.1)
- **Visible feature additions** — wishlist? product reviews? order history filtering? advanced search filters? — TBD theo user feedback
- **Backend hardening (chọn lọc)** — pick từ D1..D17 nếu có visible impact; defer phần invisible
- **Nyquist VALIDATION.md** — Phase 6 + Phase 8 + future phases (cân nhắc ưu tiên)
- **Playwright E2E re-baseline** — update suite cho v1.1 features (auth/admin/order detail)

<details>
<summary>Previous milestones (shipped)</summary>

**v1.0 — MVP Stabilization** (2026-04-25)
- Complete CRUD endpoints across all microservices (consistent patterns)
- Swagger/OpenAPI documentation for every service and gateway
- Frontend-backend API contract alignment (DTOs, status codes, error formats)
- Cross-service validation and consistent error handling
- 11/11 REQs MET, Playwright 12/12 PASS

**v1.1 — Real End-User Experience** (2026-04-26)
- C0 Database Foundation — Postgres + JPA + Flyway + seed từ FE mocks
- C1 Real auth flow — backend `/api/users/auth/*` + JWT + FE form gỡ mock
- C2 Admin + Search real CRUD qua gateway
- C3 Cart → Order persistence visible — full breakdown
- 15/19 SATISFIED + 4 PARTIAL (residual defer v1.2)
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
*Last updated: 2026-04-26 — Milestone v1.1 Real End-User Experience SHIPPED (15/19 SATISFIED + 4 PARTIAL deferred v1.2).*
