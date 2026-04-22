# Roadmap: PTIT HTPT E-Commerce Platform

## Overview

Stabilize the existing microservices + gateway + Next.js frontend by making the API surface consistent, complete, and well-documented (Swagger/OpenAPI), then validate the full shopping journey end-to-end with predictable validation and error handling.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [ ] **Phase 1: API Contract & Swagger Baseline** - Establish consistent API patterns and Swagger/OpenAPI everywhere
- [ ] **Phase 2: CRUD Completeness Across Services** - Close CRUD gaps across services using the Phase 1 contract
- [ ] **Phase 3: Validation & Error Handling Hardening** - Standardize validation and error handling behaviors
- [ ] **Phase 4: Frontend Contract Alignment + E2E Validation** - Align frontend with contracts and validate shopping flow

## Phase Details

### Phase 1: API Contract & Swagger Baseline
**Goal**: Define and enforce a consistent API contract across gateway and services, with Swagger/OpenAPI available everywhere.
**Depends on**: Nothing (first phase)
**Requirements**: API-01, API-02, API-03, API-04
**Success Criteria** (what must be TRUE):
  1. Swagger/OpenAPI is accessible for every service and reflects real endpoints/DTOs.
  2. Error responses follow a single documented format across services (including gateway-proxied errors).
  3. Status codes and response envelopes are consistent across services for the common CRUD patterns.
**Plans**: TBD

Plans:
- [x] 01-01: Define and document the standard API response + error schema
- [x] 01-02: Ensure Swagger/OpenAPI is enabled and consistent in every service
- [x] 01-03: Gateway API surface conventions and compatibility checks

### Phase 2: CRUD Completeness Across Services
**Goal**: Close CRUD endpoint gaps across all services while following the Phase 1 contract patterns.
**Depends on**: Phase 1
**Requirements**: CRUD-01, CRUD-02, CRUD-03, CRUD-04, CRUD-05, CRUD-06
**Success Criteria** (what must be TRUE):
  1. Each service exposes a complete, documented CRUD set for the assignment scope.
  2. CRUD endpoints follow consistent naming, payload, and pagination conventions where relevant.
  3. API Gateway routes to all CRUD endpoints without ad-hoc inconsistencies.
**Plans**: 3 plans

Plans:
- [ ] 02-01-PLAN.md — Inventory + Notification CRUD audit and gap closure
- [ ] 02-02-PLAN.md — User + Product CRUD audit and gap closure
- [ ] 02-03-PLAN.md — Order + Payment CRUD audit and gap closure

### Phase 3: Validation & Error Handling Hardening
**Goal**: Make validation and error handling consistent and predictable across services and gateway.
**Depends on**: Phase 2
**Requirements**: VAL-01, VAL-02, VAL-03
**Success Criteria** (what must be TRUE):
  1. Invalid requests return structured field errors (same format across services).
  2. Common failure modes (not found, conflict, unauthorized/forbidden) behave consistently across services.
  3. Gateway and services produce compatible error shapes for frontend handling.
**Plans**: TBD

Plans:
- [ ] 03-01: Standardize validation + exception handling in all services
- [ ] 03-02: Align gateway error propagation and auth error behaviors

### Phase 4: Frontend Contract Alignment + E2E Validation
**Goal**: Ensure the Next.js frontend is aligned to the backend contracts and the key flows behave reliably.
**Depends on**: Phase 3
**Requirements**: FE-01, FE-02
**Success Criteria** (what must be TRUE):
  1. Frontend uses the documented endpoints/DTOs and handles standardized errors without breaking UX.
  2. Shopping flow is validated end-to-end locally: browse → cart → checkout → payment (mock) → confirmation.
  3. Failures during checkout (validation, stock, payment) are surfaced clearly and recoverably in the UI.
**Plans**: TBD

Plans:
- [ ] 04-01: Align frontend API client + DTOs to OpenAPI and validate E2E flows

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. API Contract & Swagger Baseline | 3/3 | Verification needed | 2026-04-22 |
| 2. CRUD Completeness Across Services | 0/3 | Not started | - |
| 3. Validation & Error Handling Hardening | 0/TBD | Not started | - |
| 4. Frontend Contract Alignment + E2E Validation | 0/TBD | Not started | - |

