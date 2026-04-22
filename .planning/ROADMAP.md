# Roadmap — Milestone v1.0 MVP Stabilization

**Milestone goal:** Make the API surface consistent and complete (CRUD + Swagger/OpenAPI) and align frontend-backend contracts so E2E validation is reliable.

## Phases

### Phase 1 — API Contract & Swagger Baseline

**Goal:** Define and enforce a consistent API contract across gateway and services, with Swagger/OpenAPI available everywhere.

**Requirements:** API-01, API-02, API-03, API-04

**Success criteria:**
1. Swagger/OpenAPI is accessible for every service and reflects real endpoints/DTOs.
2. Error responses follow a single documented format across services (including gateway-proxied errors).
3. Status codes and response envelopes are consistent across services for the common CRUD patterns.

---

### Phase 2 — CRUD Completeness Across Services

**Goal:** Close CRUD endpoint gaps across all services while following the Phase 1 contract patterns.

**Requirements:** CRUD-01, CRUD-02, CRUD-03, CRUD-04, CRUD-05, CRUD-06

**Success criteria:**
1. Each service exposes a complete, documented CRUD set for the assignment scope.
2. CRUD endpoints follow consistent naming, payload, and pagination conventions where relevant.
3. API Gateway routes to all CRUD endpoints without ad-hoc inconsistencies.

---

### Phase 3 — Validation & Error Handling Hardening

**Goal:** Make validation and error handling consistent and predictable across services and gateway.

**Requirements:** VAL-01, VAL-02, VAL-03

**Success criteria:**
1. Invalid requests return structured field errors (same format across services).
2. Common failure modes (not found, conflict, unauthorized/forbidden) behave consistently across services.
3. Gateway and services produce compatible error shapes for frontend handling.

---

### Phase 4 — Frontend Contract Alignment + E2E Flow Validation

**Goal:** Ensure the Next.js frontend is aligned to the backend contracts and the key flows behave reliably.

**Requirements:** FE-01, FE-02

**Success criteria:**
1. Frontend uses the documented endpoints/DTOs and handles standardized errors without breaking UX.
2. Shopping flow is validated end-to-end in a local environment: browse → cart → checkout → payment (mock) → confirmation.
3. Failures during checkout (validation, stock, payment) are surfaced clearly and recoverably in the UI.

