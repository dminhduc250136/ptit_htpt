# PTIT HTPT E-Commerce Platform — Project Charter

## What This Is

A laptop e-commerce platform built with Spring Boot microservices and Next.js frontend. Demonstrates distributed architecture patterns for university assignment, supporting complete shopping flow from product browsing through checkout and payment.

**Core Value:** Enable students to understand and implement modern microservices architecture in a realistic e-commerce context.

## Context

- **Domain:** E-commerce (B2C — individual consumers)
- **Stage:** Early/MVP — core features needed
- **Key Users:** Laptop buyers browsing, comparing, ordering online
- **Tech Stack:** Spring Boot (microservices), Next.js (frontend), Docker, PostgreSQL
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

### Active

- [ ] Full end-to-end shopping flow validation (browse → add to cart → checkout → payment → order confirmation)
- [ ] Proper error handling and validation across all services
- [ ] Inter-service communication patterns (synchronous/asynchronous)
- [ ] Database schema consistency and migrations
- [ ] Frontend-backend API contract alignment
- [ ] Docker deployment and orchestration
- [ ] Unit and integration test coverage
- [ ] API documentation (Swagger/OpenAPI)
- [ ] Input validation and security (SQL injection, XSS prevention)

### Out of Scope

- Real payment gateway integration (mock is sufficient for assignment) — focus is on architecture, not commerce
- Advanced features (recommendations, analytics, AI) — keep scope focused on core patterns
- Production-grade infrastructure (load balancing, failover) — MVP sufficient
- Mobile app — web-only MVP
- Real-time features (WebSockets, live notifications) — polling/events sufficient

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Microservices over monolith | Demonstrates distributed patterns for assignment | Chosen |
| Spring Boot + Java | Industry standard, matches assignment requirements | Locked |
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
- University assignment timeline (weeks, not months)
- Team is students learning the pattern (not production ops experience)
- Local development environment (Docker Compose, not K8s)

**Assumptions:**
- Existing codebase has proper project structure
- Database migrations are handled
- Frontend/backend communication is REST-based
- No external API integrations needed (mock data sufficient)

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

## Next Milestone Goals

Carry-over từ v1.0 đã được phân loại thành 4 cluster (xem `.planning/milestones/v1.0-REQUIREMENTS.md` §Future):
- **Auth thật:** Backend `/auth/{login,register,logout}` + JWT enforcement
- **Backend persistence + service integration:** ProductEntity.stock, OrderItem rows, inventory.reserve, payment-service vào checkout chain
- **Security hardening:** JWT claim verification ở gateway, server-side price re-fetch, CSP
- **Cleanup + Observability:** FE `/search`, `admin/*` migrate, sibling-service handleFallback rollout, integration test suite, centralized tracing

Định hình milestone tiếp theo qua `/gsd-new-milestone`.

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
*Last updated: 2026-04-25 — Milestone v1.0 MVP Stabilization SHIPPED (4 phases, 14 plans, 57 commits, Playwright 12/12 PASS)*
