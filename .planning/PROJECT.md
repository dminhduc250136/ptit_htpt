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

### Active (v1.1 — Real End-User Experience)

- [ ] **AUTH-01..03**: Real auth flow (backend `/api/users/auth/{login,register,logout}` + JWT issuance + FE form thật call backend, session persist)
- [ ] **UI-01**: `/search` page rewire → `listProducts({keyword})` real data (hết placeholder/mock)
- [ ] **UI-02**: Admin pages migrate khỏi mock → CRUD thật qua gateway (products/orders/users list/edit/delete)
- [ ] **PERSIST-01**: ProductEntity.stock persisted (A4 add-to-cart respect stock thật, hết "cart-seed via localStorage")
- [ ] **PERSIST-02**: OrderEntity persist per-item OrderItem rows + shippingAddress + paymentMethod (order detail page show full breakdown đúng)

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

**Shipped:** v1.0 — MVP Stabilization (2026-04-25)

11/11 requirements MET. Shopping flow validated end-to-end via Playwright (12/12 PASS) trên live docker-compose stack: browse → cart → checkout → mock payment → confirmation. Cross-phase audit `ready_to_complete` (xem `.planning/milestones/v1.0-ROADMAP.md` + `.planning/v1.0-MILESTONE-AUDIT.md`).

Foundation đã có:
- 6 services + gateway emit unified `ApiErrorResponse` envelope với traceId propagation
- Springdoc Swagger UI + OpenAPI codegen pipeline (FE 6 typed modules)
- CRUD completeness + soft-delete baseline + admin/public route boundaries
- Validation & error handling hardened (gateway pass-through + common-code taxonomy)
- FE typed HTTP tier + ApiError dispatcher (5 failure branches) + middleware route protection

## Current Milestone: v1.1 Real End-User Experience

**Goal:** Biến demo flow từ "stub-verified" thành "real visible end-to-end" — mọi thứ user click trên UI phải hoạt động với real data thay vì mock/seeded.

**Target features (visible-first):**
- **C1. Auth flow thật** — Backend `/api/users/auth/{login,register,logout}` + JWT issuance + FE login/register/logout call backend, session persist sau reload
- **C2. Admin + Search real data** — `/search` page rewire `listProducts({keyword})` real; `admin/*` pages migrate khỏi mock → CRUD thật qua gateway
- **C3. Cart → Order persistence visible** — ProductEntity.stock persist (hết "cart-seed via localStorage"); OrderEntity persist per-item rows + shippingAddress + paymentMethod (order detail page show full breakdown đúng)

**Deferred sang v1.2** (invisible to end-user, xem `.planning/v1.0-MILESTONE-AUDIT.md` D1/D2/D6/D7/D8/D9/D10/D12/D13/D14/D17): inventory.reserve real, payment-service vào checkout chain, CSP, server-side price re-fetch, gateway JWT claim verification, observability/tracing (OBS-01), integration test suite (TEST-01), sibling handleFallback rollout, code review WR/IN carry-over, FE legacy types cleanup.

<details>
<summary>Previous milestone target features (v1.0 — shipped)</summary>

- Complete CRUD endpoints across all microservices (consistent patterns)
- Swagger/OpenAPI documentation for every service and gateway
- Frontend-backend API contract alignment (DTOs, status codes, error formats)
- Cross-service validation and consistent error handling
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
*Last updated: 2026-04-25 — Milestone v1.1 Real End-User Experience STARTED (visible-first scope: Auth real + Admin/Search real data + Cart→Order persistence visible).*
