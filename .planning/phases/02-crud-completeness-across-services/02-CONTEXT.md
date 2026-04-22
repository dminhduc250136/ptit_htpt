# Phase 2: CRUD Completeness Across Services - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Close CRUD endpoint gaps for user, product, order, payment, inventory, and notification services while preserving the Phase 1 API contract patterns and gateway consistency.

</domain>

<decisions>
## Implementation Decisions

### CRUD scope by service
- **D-01:** Phase 2 uses a full-uniform CRUD baseline across all six services (Create, Read/List, Update, Delete) unless a strict domain exception is explicitly documented.
- **D-02:** Delete behavior defaults to soft delete (status/isDeleted style) as the safe baseline; hard delete is exception-only.

### Endpoint contract and pagination
- **D-03:** CRUD endpoints follow REST resource style (`GET /resources`, `GET /resources/{id}`, `POST`, `PUT/PATCH`, `DELETE`) rather than verb-based routes.
- **D-04:** List endpoints standardize query params as `page`, `size`, `sort`.
- **D-05:** List responses standardize metadata as `content`, `totalElements`, `totalPages`, `currentPage`, `pageSize`, `isFirst`, `isLast`.

### Admin/public boundary
- **D-06:** Admin and public APIs must be explicitly separated by route prefix (admin-prefixed endpoints plus independent auth policy), not only by role checks on shared paths.
- **D-07:** Admin endpoint coverage is mandatory in this phase for Product, Inventory, Order, and User domains.

### Gateway rollout strategy
- **D-08:** Keep existing gateway service prefixes unchanged: `/api/users`, `/api/products`, `/api/orders`, `/api/payments`, `/api/inventory`, `/api/notifications`.
- **D-09:** Gateway updates and contract smoke validation happen in the same rollout cycle as each service CRUD expansion (no delayed gateway catch-up at end of phase).

### Claude's Discretion
- Exact DTO field naming and request/response class decomposition per service.
- Per-service exception cases where hard delete is truly required.
- Internal controller/package structure as long as route and contract rules above are preserved.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope and acceptance
- `.planning/ROADMAP.md` — Phase 2 goal, dependencies, requirements, and success criteria.
- `.planning/REQUIREMENTS.md` — CRUD-01..CRUD-06 requirement definitions and milestone constraints.
- `.planning/PROJECT.md` — project-level architecture constraints and milestone intent.

### Existing backend routing/contract anchors
- `sources/backend/api-gateway/src/main/resources/application.yml` — current gateway prefixes, rewrite rules, and cross-service route conventions.

### Existing frontend contract expectation
- `sources/frontend/src/services/api.ts` — current list/pagination shape used by frontend service abstraction.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ContractSmokeController` pattern across services (`/ping`, `/validate`) can be extended as a lightweight contract/smoke guard for new CRUD endpoints.
- `ApiResponse`, `ApiResponseAdvice`, and `GlobalExceptionHandler` classes already exist in services and can anchor consistent envelope/error responses.

### Established Patterns
- Gateway currently enforces per-service API prefix + rewrite pattern, which should remain stable while CRUD endpoints are expanded under each prefix.
- Services already include standardized response/error wrapper scaffolding from Phase 1 work; Phase 2 should reuse that instead of creating custom ad-hoc response shapes.

### Integration Points
- CRUD endpoint additions in each service must be mirrored in gateway route expectations and smoke validation.
- Frontend API client migration from mock data to real APIs depends on stable list/pagination contract alignment.

</code_context>

<specifics>
## Specific Ideas

- Prioritize contract stability over shortcut endpoint additions: route naming and pagination shape must remain predictable for frontend alignment in later phases.
- Keep gateway prefixes unchanged to avoid unnecessary frontend path churn.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 02-crud-completeness-across-services*
*Context gathered: 2026-04-22*
