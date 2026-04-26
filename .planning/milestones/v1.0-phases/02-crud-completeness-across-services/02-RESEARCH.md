# Phase 02 Research: CRUD Completeness Across Services

## Objective

Research implementation approach for completing CRUD coverage across six services while preserving Phase 1 API contract, gateway prefixes, and frontend pagination shape.

## Inputs Reviewed

- `.planning/ROADMAP.md`
- `.planning/REQUIREMENTS.md`
- `.planning/phases/02-crud-completeness-across-services/02-CONTEXT.md`
- `.planning/phases/01/01-01-SUMMARY.md`
- `.planning/phases/01/01-02-SUMMARY.md`
- `.planning/phases/01/01-03-SUMMARY.md`
- `sources/backend/api-gateway/src/main/resources/application.yml`
- `sources/frontend/src/services/api.ts`
- `architecture/services/*.md`
- `technical-spec/*` (admin-order/admin-product/admin-user/checkout-payment)

## Standard Stack (Locked)

- Java 17 + Spring Boot 3.3.x for all services
- Spring MVC controllers with existing `ApiResponse`/`ApiErrorResponse` wrappers
- Spring Cloud Gateway with existing route prefixes
- Maven per-service build and verification

No new framework dependency is required for Phase 2 planning. The phase should reuse the existing Phase 1 contract baseline.

## Required Contract Patterns

- REST resource-style CRUD routes (per D-03)
- List query params: `page`, `size`, `sort` (per D-04)
- List response metadata: `content`, `totalElements`, `totalPages`, `currentPage`, `pageSize`, `isFirst`, `isLast` (per D-05)
- Default soft delete behavior (per D-02)
- Admin/public route separation by prefix and auth policy (per D-06)
- Gateway prefixes remain unchanged (per D-08)

## Architectural Responsibility Map

| Domain | Owner Service | In-Scope CRUD Focus | Out of Scope for Phase 2 |
|---|---|---|---|
| Users | user-service | user profile/address + admin user CRUD/boundary | product/order/payment data writes |
| Products | product-service | product/category/review CRUD + admin routes | inventory stock ledger ownership |
| Orders | order-service | cart/order/admin lifecycle CRUD | VNPay signature source-of-truth logic |
| Payments | payment-service | payment session/lookup/update/cancel callback endpoints | order state machine ownership |
| Inventory | inventory-service | inventory item/reservation CRUD + admin adjust | product metadata |
| Notifications | notification-service | template/config/dispatch log CRUD + dispatch endpoints | order/payment state decisions |

## Gateway Implications

Current gateway route prefixes already support nested CRUD paths via wildcard rewrite. Phase 2 should not rename prefixes and should pair each service rollout with gateway-level contract smoke validation in the same plan cycle (per D-09).

## Frontend Contract Anchor

`sources/frontend/src/services/api.ts` already expects pagination fields:

- `content`
- `totalElements`
- `totalPages`
- `currentPage`
- `pageSize`
- `isFirst`
- `isLast`

Backend list endpoints in this phase must match this shape to avoid frontend contract churn in Phase 4.

## Common Pitfalls to Avoid

1. Mixing admin and public routes on a single path with role checks only.
2. Returning ad-hoc list metadata (`items`, `total`) instead of the standardized pagination shape.
3. Implementing hard delete by default without explicit domain exception documentation.
4. Updating service endpoints without same-cycle gateway smoke validation.
5. Returning unwrapped success payloads that bypass Phase 1 `ApiResponseAdvice` behavior.

## Discovery Level

Level 0/1 hybrid: existing stack and patterns are already known and present in repository; no external dependency research required.

## Planning Guidance

- Split plans by service pair to preserve context quality and map directly to CRUD requirement IDs.
- Keep plans autonomous and non-overlapping by file ownership.
- Include explicit verification commands per service and gateway path checks.

## Research Confidence

High confidence for stack and contract conventions; medium confidence for per-domain edge transitions where technical specs are broader than Phase 2 scope.
