# Phase 3: Validation & Error Handling Hardening - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Standardize validation and error handling behavior across all backend services and API gateway so frontend receives predictable, compatible error contracts.

This phase covers contract consistency and mapping behavior only, not new business capabilities.

</domain>

<decisions>
## Implementation Decisions

### Validation field errors
- **D-01:** `fieldErrors` only includes `rejectedValue` for non-sensitive fields. Sensitive fields (password/token/secret and similar) must be masked; long values should be truncated.
- **D-02:** Validation responses use hybrid format: stable machine-readable error `code` plus specific human-readable `message`.

### Business error mapping and HTTP status
- **D-03:** Use standardized common error codes for cross-service consistency (`NOT_FOUND`, `BAD_REQUEST`, `CONFLICT`, `VALIDATION_ERROR`, `INTERNAL_ERROR`), and include optional domain-specific detail in `details` (for example `domainCode`) when needed.
- **D-04:** Standardize reason/message templates for common failure modes across all services instead of free-form per-service wording.

### Gateway downstream error propagation
- **D-05:** Gateway uses pass-through for downstream service errors when the body already matches the standard error schema; otherwise gateway normalizes to standard schema.
- **D-06:** Gateway preserves downstream status code and structured fields (`code`, `message`, `fieldErrors`) whenever pass-through applies.
- **D-07:** Gateway-originated failures (for example missing route) must still return the same standard error schema.

### Auth/AuthZ error contract
- **D-08:** Unauthorized cases return `401` with standardized code `UNAUTHORIZED` and no field-level validation payload.
- **D-09:** Forbidden cases return `403` with standardized code `FORBIDDEN` and no field-level validation payload.
- **D-10:** Gateway and services must keep `401` and `403` distinct and never collapse them into generic `500` fallback for normal auth/authz failures.

### the agent's Discretion
- Exact implementation mechanism for shared error mapping (shared utility vs per-service constants).
- Exact masking/truncation rule details (max lengths, sensitive field name list governance).
- Internal class/package structure, as long as contract output remains consistent with the decisions above.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope and requirement source
- `.planning/ROADMAP.md` — Phase 3 goal, dependencies, and success criteria.
- `.planning/REQUIREMENTS.md` — Requirement IDs `VAL-01`, `VAL-02`, `VAL-03`.
- `.planning/PROJECT.md` — milestone constraints and non-goals.
- `.planning/phases/02-crud-completeness-across-services/02-CONTEXT.md` — carried-forward contract and gateway prefix decisions.

### Existing error/validation contract implementation
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/GlobalExceptionHandler.java` — validation/fallback mapping baseline in services.
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/ApiErrorResponse.java` — service error payload shape baseline.
- `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/gateway/GlobalGatewayErrorHandler.java` — gateway global error behavior.
- `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/gateway/ApiErrorResponse.java` — gateway error payload shape.
- `sources/backend/api-gateway/src/main/resources/application.yml` — gateway routing and rewrite rules that constrain propagation behavior.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Each service already has `GlobalExceptionHandler`, `ApiErrorResponse`, and `ApiResponseAdvice` patterns that can be hardened instead of redesigned.
- Trace propagation baseline exists via service `TraceIdFilter` and gateway request ID handling.

### Established Patterns
- Services already use `@Valid` and `MethodArgumentNotValidException`/`BindException` handling with `fieldErrors` list.
- Services use `ResponseStatusException` for many domain failures, which is a good central hook for standardized mapping.
- Gateway has centralized reactive error handler with standardized error body shape.

### Integration Points
- Service-side error normalization and gateway pass-through/normalization rules must align to keep frontend handling stable.
- Frontend-facing compatibility depends on keeping `status`, `code`, `message`, `path`, `traceId`, and `fieldErrors` contract predictable in both service-origin and gateway-origin failures.

</code_context>

<specifics>
## Specific Ideas

- Decisions for Area 2-4 were delegated to the agent and locked using recommended defaults to reduce ambiguity for planning.
- Priority is predictable frontend integration in Phase 4 over per-service message freedom.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 03-validation-error-handling-hardening*
*Context gathered: 2026-04-22*
