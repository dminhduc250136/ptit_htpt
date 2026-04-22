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

### Active

- [ ] Ensure all microservices have complete CRUD endpoints
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

## Current Milestone: v1.0 MVP Stabilization

**Goal:** Make the API surface consistent and complete (CRUD + Swagger/OpenAPI) so the frontend can reliably integrate and the system can be validated end-to-end.

**Target features:**
- Complete CRUD endpoints across all microservices (consistent patterns)
- Swagger/OpenAPI documentation for every service and gateway
- Frontend-backend API contract alignment (DTOs, status codes, error formats)
- Cross-service validation and consistent error handling

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
*Last updated: 2026-04-22 — milestone v1.0 started*
