# Phase 03 Research: Validation & Error Handling Hardening

## Objective

Research implementation approach to make validation and error handling behavior consistent across all backend services and API gateway, aligned with `VAL-01`, `VAL-02`, and `VAL-03`.

## Inputs Reviewed

- `.planning/ROADMAP.md`
- `.planning/REQUIREMENTS.md`
- `.planning/phases/03-validation-error-handling-hardening/03-CONTEXT.md`
- `.planning/phases/02-crud-completeness-across-services/02-CONTEXT.md`
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/GlobalExceptionHandler.java`
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/ApiErrorResponse.java`
- `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/gateway/GlobalGatewayErrorHandler.java`
- `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/gateway/ApiErrorResponse.java`
- `sources/backend/api-gateway/src/main/resources/application.yml`

## Standard Stack (Locked)

- Java 17 + Spring Boot 3.3.x
- Spring MVC + `@RestControllerAdvice` at service level
- Spring Cloud Gateway reactive global error handler at gateway level
- Existing `ApiResponse` and `ApiErrorResponse` schema family
- Existing trace propagation (`TraceIdFilter` in services, request-id handling in gateway)

No new framework is required for this phase. Priority is standardization over technology change.

## Validation Architecture

### Service-side validation and exception flow

- Keep request DTO validation through `@Valid`.
- Keep service-level global handlers as the single mapping point for:
  - `MethodArgumentNotValidException`
  - `BindException`
  - `HttpMessageNotReadableException`
  - fallback `Exception`
- Normalize all service error outputs to the same envelope keys:
  `timestamp`, `status`, `error`, `message`, `code`, `path`, `traceId`, `fieldErrors`.

### Cross-service error-code contract

- Use shared common codes for frontend compatibility:
  `VALIDATION_ERROR`, `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `UNAUTHORIZED`, `FORBIDDEN`, `INTERNAL_ERROR`.
- Optional domain detail can be attached in details payload (for example domain-specific code) without replacing common code.

### Sensitive data policy for field errors

- Return `rejectedValue` only for non-sensitive fields.
- Mask sensitive values (password/token/secret and equivalent fields).
- Truncate long rejected values to avoid payload bloat and accidental data leak.

### Gateway compatibility policy

- Pass-through downstream error response if schema is already compliant.
- Normalize at gateway only when downstream payload is missing required shape.
- Gateway-originated failures (missing routes, internal gateway errors) must use the same envelope.

### Auth/AuthZ error policy (VAL-03)

- `401` must map to `UNAUTHORIZED`.
- `403` must map to `FORBIDDEN`.
- Avoid collapsing auth/authz failures into generic `500` fallback behavior.

## Architectural Responsibility Map

| Component | Responsibility in Phase 3 | Not in Phase 3 |
|---|---|---|
| user-service | Define reusable service-side hardening pattern (reference implementation) | New auth features |
| product/order/payment/inventory/notification services | Apply same validation/error mapping policy and code taxonomy | New business endpoints |
| api-gateway | Preserve status/code compatibility, normalize only when needed | Route redesign |
| frontend (next phase consumer) | Not modified in this phase; contract consumer only | UI changes |

## Common Pitfalls to Avoid

1. Service-specific free-text error messages that break frontend assumptions.
2. Inconsistent use of `code` (`NOT_FOUND` vs ad-hoc naming) across services.
3. Exposing sensitive `rejectedValue` in validation failures.
4. Gateway rewriting already-valid downstream errors, causing data loss.
5. Treating `401` and `403` as interchangeable outcomes.

## Discovery Level

Level 1 (quick verification): Existing codebase already contains relevant exception-handling and error-envelope scaffolding.

## Planning Guidance

- Split planning into two slices:
  - Service-side standardization (`VAL-01`, `VAL-02`)
  - Gateway + auth/authz consistency (`VAL-03` and compatibility)
- Use one service as canonical pattern first, then propagate to remaining services to reduce drift.
- Include explicit verification commands for malformed JSON, field validation, not-found, and auth/authz cases.

## Research Confidence

High confidence for service/gateway envelope and validation mechanics; medium confidence for auth/authz paths because explicit security handler wiring is limited in current codebase and will need contract-first hardening.
